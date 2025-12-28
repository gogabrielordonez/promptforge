package com.adwaizer.promptforge.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Target AI system for optimization
 * Each target has specific prompt engineering patterns
 */
enum class TargetAI(val displayName: String, val systemHints: String) {
    CLAUDE(
        displayName = "Claude",
        systemHints = """
            - Use XML tags for structured sections when helpful
            - Encourage chain-of-thought with phrases like "think step by step"
            - Claude responds well to explicit constraints and examples
            - Can handle very long, detailed instructions
        """.trimIndent()
    ),
    GPT(
        displayName = "ChatGPT",
        systemHints = """
            - Works well with role-based framing ("You are a...")
            - Responds to numbered step instructions
            - Benefit from explicit output format specification
            - Good with few-shot examples
        """.trimIndent()
    ),
    GEMINI(
        displayName = "Gemini",
        systemHints = """
            - Optimized for multimodal cues
            - Handles structured JSON output requests well
            - Benefits from clear task decomposition
            - Good at following detailed formatting instructions
        """.trimIndent()
    ),
    LOCAL(
        displayName = "Local Model",
        systemHints = """
            - Keep prompts concise (limited context window)
            - Use simple, direct instructions
            - Avoid complex nested structures
            - One task per prompt works best
        """.trimIndent()
    ),
    GENERIC(
        displayName = "Any AI",
        systemHints = """
            - Universal prompt engineering principles
            - Clear task statement
            - Explicit output format
            - Relevant context included
        """.trimIndent()
    )
}

/**
 * Enhancement intensity level
 */
enum class EnhancementLevel(val displayName: String, val description: String) {
    MINIMAL(
        displayName = "Light Touch",
        description = "Fix clarity issues, add basic structure"
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Add specificity, structure, and constraints"
    ),
    MAXIMUM(
        displayName = "Full Enhancement",
        description = "Complete rewrite with all best practices"
    )
}

/**
 * Request to enhance a prompt
 */
data class EnhancementRequest(
    val originalPrompt: String,
    val targetAI: TargetAI = TargetAI.GENERIC,
    val level: EnhancementLevel = EnhancementLevel.BALANCED,
    val templateId: String? = null,
    val additionalContext: String? = null,
    val sourceApp: String? = null
)

/**
 * Result of enhancement
 */
data class EnhancementResult(
    val originalPrompt: String,
    val enhancedPrompt: String,
    val targetAI: TargetAI,
    val level: EnhancementLevel,
    val inferenceTimeMs: Long,
    val tokensGenerated: Int,
    val improvements: List<String> = emptyList(),
    val timestamp: Instant = Instant.now()
)

/**
 * Enhancement template for common tasks
 */
@Entity(tableName = "templates")
data class Template(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val basePrompt: String,
    val placeholders: List<String> = emptyList(),
    val isBuiltIn: Boolean = true,
    val usageCount: Int = 0,
    val lastUsed: Instant? = null
)

enum class TemplateCategory(val displayName: String) {
    CODING("Code & Development"),
    WRITING("Writing & Content"),
    ANALYSIS("Analysis & Research"),
    CREATIVE("Creative & Ideation"),
    BUSINESS("Business & Professional"),
    CUSTOM("My Templates")
}

/**
 * History entry for analytics
 */
@Entity(tableName = "enhancement_history")
data class EnhancementHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalPrompt: String,
    val enhancedPrompt: String,
    val targetAI: String,
    val level: String,
    val inferenceTimeMs: Long,
    val sourceApp: String?,
    val wasUsed: Boolean = false,
    val wasEdited: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * User preferences for enhancement behavior
 */
data class EnhancementPreferences(
    val defaultTargetAI: TargetAI = TargetAI.GENERIC,
    val defaultLevel: EnhancementLevel = EnhancementLevel.BALANCED,
    val autoCopyToClipboard: Boolean = true,
    val showNotificationOnComplete: Boolean = true,
    val hapticFeedback: Boolean = true,
    val keepModelLoaded: Boolean = true,
    val preferredTemplates: List<String> = emptyList()
)
