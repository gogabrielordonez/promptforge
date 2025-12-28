package com.adwaizer.promptforge.core

import com.adwaizer.promptforge.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core engine for prompt enhancement
 * Applies prompt engineering best practices using Gemma 2B
 */
@Singleton
class EnhancementEngine @Inject constructor(
    private val inference: GemmaInference
) {
    companion object {
        private const val TAG = "EnhancementEngine"
    }

    /**
     * Enhance a prompt with full options
     */
    suspend fun enhance(request: EnhancementRequest): Result<EnhancementResult> = 
        withContext(Dispatchers.Default) {
            try {
                // Build the enhancement prompt
                val systemPrompt = buildEnhancementPrompt(
                    targetAI = request.targetAI,
                    level = request.level,
                    template = request.templateId?.let { getTemplate(it) }
                )
                
                val fullPrompt = """
                    |$systemPrompt
                    |
                    |ORIGINAL PROMPT:
                    |${request.originalPrompt}
                    |
                    |${request.additionalContext?.let { "ADDITIONAL CONTEXT:\n$it\n" } ?: ""}
                    |ENHANCED PROMPT:
                """.trimMargin()

                // Run inference
                val timedResult = inference.generateTimed(fullPrompt).getOrThrow()
                
                // Post-process the result
                val enhancedPrompt = postProcess(timedResult.response, request.targetAI)
                val improvements = analyzeImprovements(request.originalPrompt, enhancedPrompt)

                Result.success(
                    EnhancementResult(
                        originalPrompt = request.originalPrompt,
                        enhancedPrompt = enhancedPrompt,
                        targetAI = request.targetAI,
                        level = request.level,
                        inferenceTimeMs = timedResult.inferenceTimeMs,
                        tokensGenerated = timedResult.tokensGenerated,
                        improvements = improvements
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Quick enhance with defaults
     */
    suspend fun quickEnhance(prompt: String): Result<String> {
        val request = EnhancementRequest(originalPrompt = prompt)
        return enhance(request).map { it.enhancedPrompt }
    }

    /**
     * Build the system prompt for enhancement based on options
     */
    private fun buildEnhancementPrompt(
        targetAI: TargetAI,
        level: EnhancementLevel,
        template: Template?
    ): String {
        val baseInstructions = """
            |You are PromptForge, an expert prompt engineer. Your task is to transform 
            |user prompts into optimized, high-quality instructions that will get the 
            |best possible results from AI systems.
            |
            |CORE ENHANCEMENT PRINCIPLES:
            |1. SPECIFICITY: Replace vague terms with concrete, measurable constraints
            |2. STRUCTURE: Add clear organization with sections, steps, or categories
            |3. CONTEXT: Include relevant background the AI needs to understand the task
            |4. CONSTRAINTS: Define what the output should and should NOT include
            |5. FORMAT: Specify exactly how the response should be formatted
            |6. EXAMPLES: Add examples when they would clarify expectations
            |
            |CRITICAL RULES:
            |- PRESERVE the user's original intent completely
            |- DO NOT add tasks or requirements the user didn't ask for
            |- DO NOT make assumptions about information the user should provide
            |- Keep the enhancement proportional to the original (don't over-engineer simple requests)
            |- Output ONLY the enhanced prompt, no explanations or meta-commentary
        """.trimMargin()

        val levelInstructions = when (level) {
            EnhancementLevel.MINIMAL -> """
                |ENHANCEMENT LEVEL: MINIMAL
                |- Make only essential clarity improvements
                |- Fix ambiguous language
                |- Add basic output format if missing
                |- Keep length similar to original
            """.trimMargin()
            
            EnhancementLevel.BALANCED -> """
                |ENHANCEMENT LEVEL: BALANCED
                |- Add reasonable specificity and constraints
                |- Include helpful structure
                |- Suggest output format
                |- Add 1-2 clarifying details
                |- Moderate length increase acceptable
            """.trimMargin()
            
            EnhancementLevel.MAXIMUM -> """
                |ENHANCEMENT LEVEL: MAXIMUM
                |- Comprehensive rewrite applying all best practices
                |- Detailed structure with sections if appropriate
                |- Explicit constraints and anti-patterns
                |- Include formatting specifications
                |- Add examples if they would help
                |- Thorough but not verbose
            """.trimMargin()
        }

        val targetInstructions = """
            |TARGET AI SYSTEM: ${targetAI.displayName}
            |${targetAI.systemHints}
        """.trimMargin()

        val templateInstructions = template?.let {
            """
                |TEMPLATE CONTEXT: ${it.name}
                |${it.description}
                |Base pattern: ${it.basePrompt}
            """.trimMargin()
        } ?: ""

        return listOf(
            baseInstructions,
            levelInstructions,
            targetInstructions,
            templateInstructions
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    /**
     * Post-process the model output to clean it up
     */
    private fun postProcess(rawOutput: String, targetAI: TargetAI): String {
        var processed = rawOutput.trim()
        
        // Remove any meta-commentary the model might have added
        val metaPrefixes = listOf(
            "Here is the enhanced prompt:",
            "Enhanced prompt:",
            "Here's the improved version:",
            "Optimized prompt:",
            "**Enhanced Prompt:**"
        )
        
        for (prefix in metaPrefixes) {
            if (processed.startsWith(prefix, ignoreCase = true)) {
                processed = processed.removePrefix(prefix).trim()
            }
        }
        
        // Remove wrapping quotes if present
        if (processed.startsWith("\"") && processed.endsWith("\"")) {
            processed = processed.drop(1).dropLast(1)
        }
        
        // Target-specific post-processing
        processed = when (targetAI) {
            TargetAI.CLAUDE -> {
                // Ensure XML tags are properly formatted
                processed.replace("< ", "<").replace(" >", ">")
            }
            TargetAI.GPT -> {
                // Ensure role-based framing is clean
                processed
            }
            else -> processed
        }
        
        return processed
    }

    /**
     * Analyze what improvements were made
     */
    private fun analyzeImprovements(original: String, enhanced: String): List<String> {
        val improvements = mutableListOf<String>()
        
        // Length analysis
        if (enhanced.length > original.length * 1.5) {
            improvements.add("Added detail and specificity")
        }
        
        // Structure indicators
        if (enhanced.contains("\n") && !original.contains("\n")) {
            improvements.add("Added structure")
        }
        
        // Constraint indicators
        val constraintWords = listOf("must", "should", "avoid", "don't", "exactly", "specifically")
        val originalConstraints = constraintWords.count { original.lowercase().contains(it) }
        val enhancedConstraints = constraintWords.count { enhanced.lowercase().contains(it) }
        if (enhancedConstraints > originalConstraints) {
            improvements.add("Added constraints")
        }
        
        // Format specification
        val formatIndicators = listOf("format", "structure", "organize", "bullet", "numbered", "json", "markdown")
        if (formatIndicators.any { enhanced.lowercase().contains(it) } && 
            !formatIndicators.any { original.lowercase().contains(it) }) {
            improvements.add("Specified output format")
        }
        
        // Example detection
        if ((enhanced.contains("example:") || enhanced.contains("e.g.") || enhanced.contains("such as")) &&
            !(original.contains("example:") || original.contains("e.g.") || original.contains("such as"))) {
            improvements.add("Added examples")
        }
        
        return improvements
    }

    /**
     * Get a template by ID
     */
    private fun getTemplate(templateId: String): Template? {
        // In production, this would fetch from TemplateRepository
        return BUILT_IN_TEMPLATES.find { it.id == templateId }
    }

    /**
     * Built-in templates for common use cases
     */
    companion object Templates {
        val BUILT_IN_TEMPLATES = listOf(
            Template(
                id = "code_review",
                name = "Code Review",
                description = "Optimize prompts for thorough code analysis",
                category = TemplateCategory.CODING,
                basePrompt = """
                    Review this code focusing on:
                    1. Correctness and potential bugs
                    2. Performance considerations
                    3. Code style and readability
                    4. Security vulnerabilities
                    5. Suggested improvements with code examples
                """.trimIndent()
            ),
            Template(
                id = "explain_concept",
                name = "Explain Concept",
                description = "Get clear, educational explanations",
                category = TemplateCategory.ANALYSIS,
                basePrompt = """
                    Explain this concept:
                    - Start with a one-sentence summary
                    - Provide a clear definition
                    - Give 2-3 concrete examples
                    - Explain common misconceptions
                    - Suggest next topics to learn
                """.trimIndent()
            ),
            Template(
                id = "blog_post",
                name = "Blog Post",
                description = "Create engaging blog content",
                category = TemplateCategory.WRITING,
                basePrompt = """
                    Write a blog post with:
                    - Compelling hook in the first paragraph
                    - Clear structure with subheadings
                    - Actionable insights or takeaways
                    - Engaging, conversational tone
                    - Strong conclusion with CTA
                """.trimIndent()
            ),
            Template(
                id = "brainstorm",
                name = "Brainstorm Ideas",
                description = "Generate creative ideas and possibilities",
                category = TemplateCategory.CREATIVE,
                basePrompt = """
                    Generate diverse ideas exploring:
                    - Obvious/conventional approaches
                    - Unexpected/creative angles
                    - Combination of existing concepts
                    - Challenge assumptions
                    - Rank by feasibility and impact
                """.trimIndent()
            ),
            Template(
                id = "email_draft",
                name = "Professional Email",
                description = "Craft clear, professional emails",
                category = TemplateCategory.BUSINESS,
                basePrompt = """
                    Draft a professional email that:
                    - Has a clear, specific subject line
                    - Gets to the point in the first sentence
                    - Uses appropriate tone for the relationship
                    - Includes a clear call-to-action
                    - Is concise (under 200 words)
                """.trimIndent()
            )
        )
    }
}
