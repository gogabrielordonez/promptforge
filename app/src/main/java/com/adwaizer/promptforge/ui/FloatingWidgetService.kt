package com.adwaizer.promptforge.ui

import android.animation.ValueAnimator
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import com.adwaizer.promptforge.PromptForgeApp
import com.adwaizer.promptforge.R
import com.adwaizer.promptforge.core.PromptForgeService
import com.adwaizer.promptforge.model.EnhancementRequest
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

/**
 * Floating widget that provides quick access to prompt enhancement
 * Appears as a draggable bubble on screen
 */
@AndroidEntryPoint
class FloatingWidgetService : Service() {

    companion object {
        private const val TAG = "FloatingWidgetService"
        
        const val ACTION_SHOW = "com.adwaizer.promptforge.SHOW_WIDGET"
        const val ACTION_HIDE = "com.adwaizer.promptforge.HIDE_WIDGET"
        const val ACTION_ENHANCE_CLIPBOARD = "com.adwaizer.promptforge.ENHANCE_CLIPBOARD"

        fun show(context: Context) {
            context.startForegroundService(
                Intent(context, FloatingWidgetService::class.java).apply {
                    action = ACTION_SHOW
                }
            )
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, FloatingWidgetService::class.java).apply {
                    action = ACTION_HIDE
                }
            )
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var expandedView: View? = null
    private var isExpanded = false

    private var promptForgeService: PromptForgeService? = null
    private var isBound = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PromptForgeService.LocalBinder
            promptForgeService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            promptForgeService = null
            isBound = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Bind to PromptForgeService
        Intent(this, PromptForgeService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                startForeground(
                    PromptForgeApp.NOTIFICATION_ID_SERVICE + 1,
                    createNotification()
                )
                showFloatingWidget()
            }
            ACTION_HIDE -> {
                hideFloatingWidget()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_ENHANCE_CLIPBOARD -> {
                enhanceClipboard()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        hideFloatingWidget()
        super.onDestroy()
    }

    private fun showFloatingWidget() {
        if (floatingView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Create floating bubble
        floatingView = createFloatingBubble(layoutParams)
        windowManager.addView(floatingView, layoutParams)

        // Animate entrance
        floatingView?.apply {
            scaleX = 0f
            scaleY = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    private fun createFloatingBubble(layoutParams: WindowManager.LayoutParams): View {
        val container = FrameLayout(this).apply {
            setPadding(8, 8, 8, 8)
        }

        // Main bubble button
        val bubble = Button(this).apply {
            text = "✨"
            textSize = 24f
            width = 120
            height = 120
            background = getDrawable(R.drawable.bubble_background)
            
            setOnClickListener { toggleExpanded() }
            setOnLongClickListener { 
                enhanceClipboard()
                true 
            }
        }

        // Make it draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.abs(event.rawX - initialTouchX) > 10 || 
                                Math.abs(event.rawY - initialTouchY) > 10
                    if (!moved) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        container.addView(bubble)
        return container
    }

    private fun toggleExpanded() {
        if (isExpanded) {
            hideExpandedView()
        } else {
            showExpandedView()
        }
        isExpanded = !isExpanded
    }

    private fun showExpandedView() {
        if (expandedView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        expandedView = createExpandedPanel()
        windowManager.addView(expandedView, layoutParams)
    }

    private fun createExpandedPanel(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0222222.toInt())
            setPadding(32, 24, 32, 24)

            // Header
            addView(TextView(context).apply {
                text = "✨ PromptForge"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
            })

            addView(View(context).apply {
                setBackgroundColor(0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2
                ).apply { topMargin = 16; bottomMargin = 16 }
            })

            // Clipboard preview
            val clipboardText = getClipboardText()
            addView(TextView(context).apply {
                text = "Clipboard: ${clipboardText?.take(50) ?: "(empty)"}..."
                textSize = 12f
                setTextColor(0xAAFFFFFF.toInt())
            })

            // Action buttons
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 0)

                addView(Button(context).apply {
                    text = "Enhance Clipboard"
                    setOnClickListener { enhanceClipboard() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                addView(Button(context).apply {
                    text = "Close"
                    setOnClickListener { toggleExpanded() }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 16
                    }
                })
            })
        }
    }

    private fun hideExpandedView() {
        expandedView?.let {
            windowManager.removeView(it)
            expandedView = null
        }
    }

    private fun hideFloatingWidget() {
        hideExpandedView()
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
    }

    private fun enhanceClipboard() {
        val clipboardText = getClipboardText()
        
        if (clipboardText.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Enhancing clipboard...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            try {
                val result = promptForgeService?.enhance(
                    EnhancementRequest(originalPrompt = clipboardText)
                )

                result?.fold(
                    onSuccess = { enhancementResult ->
                        setClipboardText(enhancementResult.enhancedPrompt)
                        Toast.makeText(
                            this@FloatingWidgetService,
                            "Enhanced! Paste to use.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@FloatingWidgetService,
                            "Error: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@FloatingWidgetService,
                    "Enhancement failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getClipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    private fun setClipboardText(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Enhanced Prompt", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun createNotification() = NotificationCompat.Builder(this, PromptForgeApp.CHANNEL_ID_SERVICE)
        .setContentTitle("PromptForge Widget")
        .setContentText("Tap the bubble to enhance prompts")
        .setSmallIcon(R.drawable.ic_forge)
        .setOngoing(true)
        .build()
}
