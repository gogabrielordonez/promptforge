package com.adwaizer.promptforge.core

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wrapper for Gemma 2B inference using MediaPipe LLM API
 * Handles model loading, inference, and resource management
 *
 * NOTE: This uses Gemma 1.1 2B IT (Instruction Tuned) INT4 quantized model,
 * NOT T5Gemma. The model is ~1.3GB and requires 6GB+ RAM for optimal performance.
 * Downloaded from HuggingFace: t-ghosh/gemma-tflite
 */
@Singleton
class GemmaInference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GemmaInference"

        // Model configuration - Gemma 1.1 2B IT INT4
        private const val MODEL_FILENAME = "gemma-1.1-2b-it-cpu-int4.bin"
        private const val MAX_TOKENS = 8192  // Gemma 2B supports up to 8K context
        private const val TOP_K = 40
        private const val TEMPERATURE = 0.7f
        private const val RANDOM_SEED = 42
    }

    private var llmInference: LlmInference? = null
    private var isModelLoaded = false

    /**
     * Current state of the inference engine
     */
    sealed class InferenceState {
        object NotLoaded : InferenceState()
        object Loading : InferenceState()
        object Ready : InferenceState()
        data class Error(val message: String) : InferenceState()
    }

    var state: InferenceState = InferenceState.NotLoaded
        private set

    /**
     * Initialize and load the Gemma 2B model
     * Should be called once during app startup or service start
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            return@withContext Result.success(Unit)
        }

        state = InferenceState.Loading

        try {
            val modelPath = getModelPath()

            if (!File(modelPath).exists()) {
                // Try to copy from assets
                copyModelFromAssets()
            }

            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setRandomSeed(RANDOM_SEED)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            state = InferenceState.Ready

            Result.success(Unit)
        } catch (e: Exception) {
            state = InferenceState.Error(e.message ?: "Unknown error loading model")
            Result.failure(e)
        }
    }

    /**
     * Generate response synchronously
     * Use for quick, single-turn inference
     */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        ensureModelLoaded()

        try {
            val inference = llmInference ?: throw IllegalStateException("Model not loaded")
            val response = inference.generateResponse(prompt)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate response as a streaming flow
     * Use for longer responses or progressive UI updates
     * Note: Current MediaPipe API doesn't support streaming callbacks,
     * so this emits the full response at once.
     */
    fun generateStream(prompt: String): Flow<String> = flow {
        ensureModelLoaded()

        val inference = llmInference ?: throw IllegalStateException("Model not loaded")

        // MediaPipe current API only supports sync generation
        // Emit the full response
        val fullResponse = inference.generateResponse(prompt)
        emit(fullResponse)
    }.flowOn(Dispatchers.IO)

    /**
     * Generate with timing information
     */
    suspend fun generateTimed(prompt: String): Result<TimedResponse> = withContext(Dispatchers.IO) {
        ensureModelLoaded()

        try {
            val inference = llmInference ?: throw IllegalStateException("Model not loaded")

            val startTime = System.currentTimeMillis()
            val response = inference.generateResponse(prompt)
            val endTime = System.currentTimeMillis()

            val timedResponse = TimedResponse(
                response = response,
                inferenceTimeMs = endTime - startTime,
                tokensGenerated = estimateTokenCount(response)
            )

            Result.success(timedResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if the model is ready for inference
     */
    fun isReady(): Boolean = isModelLoaded && llmInference != null

    /**
     * Release model resources
     * Call when service is destroyed or app is backgrounded
     */
    fun release() {
        llmInference?.close()
        llmInference = null
        isModelLoaded = false
        state = InferenceState.NotLoaded
    }

    /**
     * Get approximate model memory usage
     * Gemma 2B INT4 requires ~1.5GB in memory
     */
    fun getMemoryUsageMB(): Int {
        return if (isModelLoaded) 1500 else 0
    }

    // Private helpers

    private fun getModelPath(): String {
        val modelsDir = File(context.filesDir, "models")
        return File(modelsDir, MODEL_FILENAME).absolutePath
    }

    private suspend fun copyModelFromAssets() = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val targetFile = File(modelsDir, MODEL_FILENAME)

        if (!targetFile.exists()) {
            context.assets.open("models/$MODEL_FILENAME").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun ensureModelLoaded() {
        if (!isModelLoaded) {
            initialize().getOrThrow()
        }
    }

    private fun estimateTokenCount(text: String): Int {
        // Rough estimate: ~4 characters per token for English
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * Response with timing metadata
     */
    data class TimedResponse(
        val response: String,
        val inferenceTimeMs: Long,
        val tokensGenerated: Int
    ) {
        val tokensPerSecond: Float
            get() = if (inferenceTimeMs > 0) {
                (tokensGenerated * 1000f) / inferenceTimeMs
            } else 0f
    }
}
