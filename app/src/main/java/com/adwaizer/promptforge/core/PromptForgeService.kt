package com.adwaizer.promptforge.core

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.adwaizer.promptforge.MainActivity
import com.adwaizer.promptforge.PromptForgeApp
import com.adwaizer.promptforge.R
import com.adwaizer.promptforge.model.EnhancementRequest
import com.adwaizer.promptforge.model.EnhancementResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Main foreground service that keeps Gemma 2B loaded and ready
 * Provides the enhancement API to all UI entry points
 */
@AndroidEntryPoint
class PromptForgeService : Service() {

    companion object {
        private const val TAG = "PromptForgeService"
        
        // Actions
        const val ACTION_START = "com.adwaizer.promptforge.START"
        const val ACTION_STOP = "com.adwaizer.promptforge.STOP"
        const val ACTION_ENHANCE = "com.adwaizer.promptforge.ENHANCE"
        const val ACTION_COPY_RESULT = "com.adwaizer.promptforge.COPY_RESULT"
        
        // Extras
        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_TARGET = "extra_target"
        const val EXTRA_LEVEL = "extra_level"

        fun startService(context: Context) {
            val intent = Intent(context, PromptForgeService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PromptForgeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var inference: GemmaInference

    @Inject
    lateinit var enhancementEngine: EnhancementEngine

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val binder = LocalBinder()

    // Service state
    sealed class ServiceState {
        object Starting : ServiceState()
        object Ready : ServiceState()
        object Processing : ServiceState()
        data class Error(val message: String) : ServiceState()
    }

    private val _state = MutableStateFlow<ServiceState>(ServiceState.Starting)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val _lastResult = MutableStateFlow<EnhancementResult?>(null)
    val lastResult: StateFlow<EnhancementResult?> = _lastResult.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): PromptForgeService = this@PromptForgeService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        initializeModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(
                    PromptForgeApp.NOTIFICATION_ID_SERVICE,
                    createNotification()
                )
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ENHANCE -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT)
                if (prompt != null) {
                    serviceScope.launch {
                        enhance(EnhancementRequest(originalPrompt = prompt))
                    }
                }
            }
            ACTION_COPY_RESULT -> {
                _lastResult.value?.let { result ->
                    copyToClipboard(result.enhancedPrompt)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        inference.release()
        super.onDestroy()
    }

    /**
     * Initialize the Gemma 2B model
     */
    private fun initializeModel() {
        serviceScope.launch {
            _state.value = ServiceState.Starting
            
            inference.initialize().fold(
                onSuccess = {
                    _state.value = ServiceState.Ready
                    updateNotification("Ready • Tap to open")
                },
                onFailure = { error ->
                    _state.value = ServiceState.Error(error.message ?: "Failed to load model")
                    updateNotification("Error loading model")
                }
            )
        }
    }

    /**
     * Enhance a prompt (callable from bound clients)
     */
    suspend fun enhance(request: EnhancementRequest): Result<EnhancementResult> {
        _state.value = ServiceState.Processing
        updateNotification("Enhancing prompt...")

        return enhancementEngine.enhance(request).also { result ->
            result.fold(
                onSuccess = { enhancementResult ->
                    _lastResult.value = enhancementResult
                    _state.value = ServiceState.Ready
                    
                    // Auto-copy if enabled
                    copyToClipboard(enhancementResult.enhancedPrompt)
                    
                    // Show result notification
                    showResultNotification(enhancementResult)
                    updateNotification("Ready • Last: ${enhancementResult.inferenceTimeMs}ms")
                },
                onFailure = { error ->
                    _state.value = ServiceState.Error(error.message ?: "Enhancement failed")
                    updateNotification("Enhancement failed")
                }
            )
        }
    }

    /**
     * Quick enhance (convenience method)
     */
    suspend fun quickEnhance(prompt: String): Result<String> {
        return enhance(EnhancementRequest(originalPrompt = prompt))
            .map { it.enhancedPrompt }
    }

    /**
     * Check if the service is ready
     */
    fun isReady(): Boolean = _state.value == ServiceState.Ready

    /**
     * Get memory usage
     */
    fun getMemoryUsage(): Int = inference.getMemoryUsageMB()

    // Notification helpers

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PromptForgeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, PromptForgeApp.CHANNEL_ID_SERVICE)
            .setContentTitle("PromptForge")
            .setContentText("Loading model...")
            .setSmallIcon(R.drawable.ic_forge)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, PromptForgeApp.CHANNEL_ID_SERVICE)
            .setContentTitle("PromptForge")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_forge)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) 
            as android.app.NotificationManager
        notificationManager.notify(PromptForgeApp.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun showResultNotification(result: EnhancementResult) {
        val copyIntent = PendingIntent.getService(
            this, 2,
            Intent(this, PromptForgeService::class.java).apply { action = ACTION_COPY_RESULT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, PromptForgeApp.CHANNEL_ID_ENHANCEMENT)
            .setContentTitle("Prompt Enhanced ✨")
            .setContentText(result.enhancedPrompt.take(100) + "...")
            .setSmallIcon(R.drawable.ic_check)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(result.enhancedPrompt))
            .addAction(R.drawable.ic_copy, "Copy", copyIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) 
            as android.app.NotificationManager
        notificationManager.notify(PromptForgeApp.NOTIFICATION_ID_RESULT, notification)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Enhanced Prompt", text)
        clipboard.setPrimaryClip(clip)
    }
}
