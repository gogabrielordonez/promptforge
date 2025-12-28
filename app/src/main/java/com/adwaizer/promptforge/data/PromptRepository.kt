package com.adwaizer.promptforge.data

import android.content.Context
import androidx.room.*
import com.adwaizer.promptforge.model.EnhancementHistoryEntry
import com.adwaizer.promptforge.model.Template
import com.adwaizer.promptforge.model.TemplateCategory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Type converters for Room
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun toStringList(list: List<String>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun toInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun fromTemplateCategory(category: TemplateCategory): String {
        return category.name
    }

    @TypeConverter
    fun toTemplateCategory(value: String): TemplateCategory {
        return TemplateCategory.valueOf(value)
    }
}

// DAOs
@Dao
interface HistoryDao {
    @Query("SELECT * FROM enhancement_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<EnhancementHistoryEntry>>

    @Query("SELECT * FROM enhancement_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<EnhancementHistoryEntry>>

    @Query("SELECT * FROM enhancement_history WHERE id = :id")
    suspend fun getById(id: Long): EnhancementHistoryEntry?

    @Insert
    suspend fun insert(entry: EnhancementHistoryEntry): Long

    @Update
    suspend fun update(entry: EnhancementHistoryEntry)

    @Delete
    suspend fun delete(entry: EnhancementHistoryEntry)

    @Query("DELETE FROM enhancement_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM enhancement_history")
    fun getCount(): Flow<Int>

    @Query("SELECT AVG(inferenceTimeMs) FROM enhancement_history")
    fun getAverageInferenceTime(): Flow<Double?>

    @Query("SELECT targetAI, COUNT(*) as count FROM enhancement_history GROUP BY targetAI ORDER BY count DESC")
    fun getTargetUsageStats(): Flow<List<TargetUsageStat>>

    @Query("SELECT COUNT(*) FROM enhancement_history WHERE timestamp > :since")
    fun getCountSince(since: Long): Flow<Int>
}

data class TargetUsageStat(
    val targetAI: String,
    val count: Int
)

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY usageCount DESC")
    fun getAllTemplates(): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE category = :category ORDER BY usageCount DESC")
    fun getTemplatesByCategory(category: String): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE isBuiltIn = 0 ORDER BY usageCount DESC")
    fun getCustomTemplates(): Flow<List<Template>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): Template?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: Template)

    @Update
    suspend fun update(template: Template)

    @Delete
    suspend fun delete(template: Template)

    @Query("UPDATE templates SET usageCount = usageCount + 1, lastUsed = :timestamp WHERE id = :id")
    suspend fun incrementUsage(id: String, timestamp: Long = System.currentTimeMillis())
}

// Database
@Database(
    entities = [EnhancementHistoryEntry::class, Template::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PromptForgeDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile
        private var INSTANCE: PromptForgeDatabase? = null

        fun getDatabase(context: Context): PromptForgeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PromptForgeDatabase::class.java,
                    "promptforge_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repository
@Singleton
class PromptRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val database = PromptForgeDatabase.getDatabase(context)
    private val historyDao = database.historyDao()
    private val templateDao = database.templateDao()

    // History operations
    val recentHistory: Flow<List<EnhancementHistoryEntry>> = historyDao.getRecentHistory()
    val allHistory: Flow<List<EnhancementHistoryEntry>> = historyDao.getAllHistory()
    val historyCount: Flow<Int> = historyDao.getCount()
    val averageInferenceTime: Flow<Double?> = historyDao.getAverageInferenceTime()
    val targetUsageStats: Flow<List<TargetUsageStat>> = historyDao.getTargetUsageStats()

    suspend fun saveToHistory(entry: EnhancementHistoryEntry): Long {
        return historyDao.insert(entry)
    }

    suspend fun markAsUsed(id: Long) {
        historyDao.getById(id)?.let { entry ->
            historyDao.update(entry.copy(wasUsed = true))
        }
    }

    suspend fun markAsEdited(id: Long) {
        historyDao.getById(id)?.let { entry ->
            historyDao.update(entry.copy(wasEdited = true))
        }
    }

    suspend fun deleteHistoryEntry(entry: EnhancementHistoryEntry) {
        historyDao.delete(entry)
    }

    suspend fun clearHistory() {
        historyDao.deleteAll()
    }

    fun getEnhancementsToday(): Flow<Int> {
        val startOfDay = java.time.LocalDate.now()
            .atStartOfDay()
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()
        return historyDao.getCountSince(startOfDay)
    }

    // Template operations
    val allTemplates: Flow<List<Template>> = templateDao.getAllTemplates()
    val customTemplates: Flow<List<Template>> = templateDao.getCustomTemplates()

    fun getTemplatesByCategory(category: TemplateCategory): Flow<List<Template>> {
        return templateDao.getTemplatesByCategory(category.name)
    }

    suspend fun getTemplate(id: String): Template? {
        return templateDao.getById(id)
    }

    suspend fun saveTemplate(template: Template) {
        templateDao.insert(template)
    }

    suspend fun deleteTemplate(template: Template) {
        templateDao.delete(template)
    }

    suspend fun recordTemplateUsage(templateId: String) {
        templateDao.incrementUsage(templateId)
    }

    // Analytics
    data class UsageStats(
        val totalEnhancements: Int,
        val enhancementsToday: Int,
        val averageTimeMs: Double,
        val favoriteTarget: String?,
        val templatesUsed: Int
    )

    suspend fun getUsageStats(): UsageStats {
        // This would aggregate data from flows
        // Simplified for now
        return UsageStats(
            totalEnhancements = 0,
            enhancementsToday = 0,
            averageTimeMs = 0.0,
            favoriteTarget = null,
            templatesUsed = 0
        )
    }
}
