package com.adwaizer.promptforge.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.adwaizer.promptforge.model.EnhancementLevel
import com.adwaizer.promptforge.model.EnhancementPreferences
import com.adwaizer.promptforge.model.TargetAI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DEFAULT_TARGET_AI = stringPreferencesKey("default_target_ai")
        val DEFAULT_LEVEL = stringPreferencesKey("default_level")
        val AUTO_COPY = booleanPreferencesKey("auto_copy")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEEP_MODEL_LOADED = booleanPreferencesKey("keep_model_loaded")
        val WIDGET_ENABLED = booleanPreferencesKey("widget_enabled")
        val KEYBOARD_ENABLED = booleanPreferencesKey("keyboard_enabled")
        val ENHANCEMENT_COUNT = intPreferencesKey("enhancement_count")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    val preferences: Flow<EnhancementPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            EnhancementPreferences(
                defaultTargetAI = prefs[Keys.DEFAULT_TARGET_AI]?.let { 
                    TargetAI.valueOf(it) 
                } ?: TargetAI.GENERIC,
                defaultLevel = prefs[Keys.DEFAULT_LEVEL]?.let { 
                    EnhancementLevel.valueOf(it) 
                } ?: EnhancementLevel.BALANCED,
                autoCopyToClipboard = prefs[Keys.AUTO_COPY] ?: true,
                showNotificationOnComplete = prefs[Keys.SHOW_NOTIFICATION] ?: true,
                hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
                keepModelLoaded = prefs[Keys.KEEP_MODEL_LOADED] ?: true
            )
        }

    val enhancementCount: Flow<Int> = context.dataStore.data
        .map { prefs -> prefs[Keys.ENHANCEMENT_COUNT] ?: 0 }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.FIRST_LAUNCH] ?: true }

    val isWidgetEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.WIDGET_ENABLED] ?: false }

    val isKeyboardEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.KEYBOARD_ENABLED] ?: false }

    suspend fun setDefaultTarget(target: TargetAI) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_TARGET_AI] = target.name
        }
    }

    suspend fun setDefaultLevel(level: EnhancementLevel) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_LEVEL] = level.name
        }
    }

    suspend fun setAutoCopy(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_COPY] = enabled
        }
    }

    suspend fun setShowNotification(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_NOTIFICATION] = enabled
        }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_FEEDBACK] = enabled
        }
    }

    suspend fun setKeepModelLoaded(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEEP_MODEL_LOADED] = enabled
        }
    }

    suspend fun setWidgetEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_ENABLED] = enabled
        }
    }

    suspend fun setKeyboardEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.KEYBOARD_ENABLED] = enabled
        }
    }

    suspend fun incrementEnhancementCount() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ENHANCEMENT_COUNT] ?: 0
            prefs[Keys.ENHANCEMENT_COUNT] = current + 1
        }
    }

    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { prefs ->
            prefs[Keys.FIRST_LAUNCH] = false
        }
    }
}
