package com.adwaizer.promptforge.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.inputmethodservice.InputMethodService
import android.os.IBinder
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.adwaizer.promptforge.R
import com.adwaizer.promptforge.core.PromptForgeService
import com.adwaizer.promptforge.model.EnhancementRequest
import kotlinx.coroutines.*

/**
 * Custom Input Method with integrated prompt enhancement
 * 
 * This keyboard wraps the system keyboard and adds an enhancement
 * bar at the top with quick access to PromptForge features.
 */
class ForgeKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "ForgeKeyboardService"
    }

    private var promptForgeService: PromptForgeService? = null
    private var isBound = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI elements
    private lateinit var enhanceBar: LinearLayout
    private lateinit var enhanceButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PromptForgeService.LocalBinder
            promptForgeService = binder.getService()
            isBound = true
            updateUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            promptForgeService = null
            isBound = false
            updateUI()
        }
    }

    override fun onCreate() {
        super.onCreate()
        bindToPromptForgeService()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        // Create the enhancement bar
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_background))
        }

        // Enhancement bar at top
        enhanceBar = createEnhanceBar()
        container.addView(enhanceBar)

        // Note: In a production app, you'd either:
        // 1. Implement a full keyboard layout
        // 2. Use an existing keyboard library
        // 3. Delegate to a keyboard view from a library
        
        // For this prototype, we'll add a simple text view explaining the concept
        val infoText = TextView(this).apply {
            text = """
                PromptForge Keyboard
                
                The ✨ Enhance button above will optimize any text 
                in the input field before you send it.
                
                Tip: Type your prompt, tap Enhance, then send!
                
                (Full keyboard implementation would go here)
            """.trimIndent()
            setPadding(32, 32, 32, 32)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.keyboard_text))
        }
        container.addView(infoText)

        // Add a simple number row and action buttons for demo
        container.addView(createActionRow())

        return container
    }

    private fun createEnhanceBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            setBackgroundColor(ContextCompat.getColor(context, R.color.enhance_bar_background))

            // Status text
            statusText = TextView(context).apply {
                text = "PromptForge"
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.keyboard_text_secondary))
            }
            addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            // Progress indicator
            progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
                visibility = View.GONE
            }
            addView(progressBar)

            // Enhance button
            enhanceButton = Button(context).apply {
                text = "✨ Enhance"
                setOnClickListener { onEnhanceClicked() }
                isEnabled = isBound && promptForgeService?.isReady() == true
            }
            addView(enhanceButton)
        }
    }

    private fun createActionRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 16)

            // Backspace
            addView(createActionButton("⌫") {
                currentInputConnection?.deleteSurroundingText(1, 0)
            })

            // Space
            addView(createActionButton("Space", weight = 3f) {
                currentInputConnection?.commitText(" ", 1)
            })

            // Enter
            addView(createActionButton("↵") {
                currentInputConnection?.let { ic ->
                    val editorInfo = currentInputEditorInfo
                    if (editorInfo != null && 
                        (editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
                        ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
                    } else {
                        ic.commitText("\n", 1)
                    }
                }
            })
        }
    }

    private fun createActionButton(text: String, weight: Float = 1f, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
        }
    }

    private fun onEnhanceClicked() {
        val inputConnection = currentInputConnection ?: return
        
        // Get all text from the input field
        val extractedText = inputConnection.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(),
            0
        )
        
        val textToEnhance = extractedText?.text?.toString()
        
        if (textToEnhance.isNullOrBlank()) {
            Toast.makeText(this, "Nothing to enhance", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        setLoadingState(true)
        statusText.text = "Enhancing..."

        serviceScope.launch {
            try {
                val result = promptForgeService?.enhance(
                    EnhancementRequest(originalPrompt = textToEnhance)
                )

                result?.fold(
                    onSuccess = { enhancementResult ->
                        // Replace the text in the input field
                        inputConnection.setComposingRegion(0, textToEnhance.length)
                        inputConnection.commitText(enhancementResult.enhancedPrompt, 1)
                        
                        statusText.text = "Enhanced! (${enhancementResult.inferenceTimeMs}ms)"
                        Toast.makeText(
                            this@ForgeKeyboardService,
                            "Prompt enhanced!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onFailure = { error ->
                        statusText.text = "Error: ${error.message}"
                        Toast.makeText(
                            this@ForgeKeyboardService,
                            "Enhancement failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun setLoadingState(loading: Boolean) {
        enhanceButton.isEnabled = !loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun updateUI() {
        if (::enhanceButton.isInitialized) {
            enhanceButton.isEnabled = isBound && promptForgeService?.isReady() == true
            statusText.text = when {
                !isBound -> "Connecting..."
                promptForgeService?.isReady() != true -> "Loading model..."
                else -> "Ready"
            }
        }
    }

    private fun bindToPromptForgeService() {
        Intent(this, PromptForgeService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        updateUI()
    }
}
