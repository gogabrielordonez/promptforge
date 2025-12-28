package com.adwaizer.promptforge.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adwaizer.promptforge.core.PromptForgeService
import com.adwaizer.promptforge.model.EnhancementLevel
import com.adwaizer.promptforge.model.EnhancementRequest
import com.adwaizer.promptforge.model.TargetAI
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Handles shared text from other apps
 * Displays a quick enhancement UI overlay
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    private var promptForgeService: PromptForgeService? = null
    private var isBound = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to service
        Intent(this, PromptForgeService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        // Extract shared text
        val sharedText = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }

        if (sharedText.isNullOrBlank()) {
            Toast.makeText(this, "No text to enhance", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                EnhancementOverlay(
                    originalText = sharedText,
                    onEnhance = { request -> enhancePrompt(request) },
                    onDismiss = { finish() },
                    onCopy = { text -> copyAndFinish(text) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private suspend fun enhancePrompt(request: EnhancementRequest): Result<String> {
        return promptForgeService?.enhance(request)?.map { it.enhancedPrompt }
            ?: Result.failure(Exception("Service not available"))
    }

    private fun copyAndFinish(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Enhanced Prompt", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Enhanced prompt copied!", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancementOverlay(
    originalText: String,
    onEnhance: suspend (EnhancementRequest) -> Result<String>,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    var enhancedText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTarget by remember { mutableStateOf(TargetAI.GENERIC) }
    var selectedLevel by remember { mutableStateOf(EnhancementLevel.BALANCED) }
    var showOptions by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Auto-enhance on launch
    LaunchedEffect(Unit) {
        isLoading = true
        val result = onEnhance(EnhancementRequest(
            originalPrompt = originalText,
            targetAI = selectedTarget,
            level = selectedLevel
        ))
        result.fold(
            onSuccess = { enhancedText = it },
            onFailure = { error = it.message }
        )
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨ PromptForge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Original prompt (collapsed)
                Text(
                    text = "Original:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = originalText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Options toggle
                TextButton(onClick = { showOptions = !showOptions }) {
                    Text(if (showOptions) "Hide Options ▲" else "Show Options ▼")
                }

                // Options panel
                AnimatedVisibility(visible = showOptions) {
                    Column {
                        // Target AI selector
                        Text(
                            text = "Target AI:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TargetAI.values().take(4).forEach { target ->
                                FilterChip(
                                    selected = selectedTarget == target,
                                    onClick = { selectedTarget = target },
                                    label = { Text(target.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Enhancement level selector
                        Text(
                            text = "Enhancement Level:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EnhancementLevel.values().forEach { level ->
                                FilterChip(
                                    selected = selectedLevel == level,
                                    onClick = { selectedLevel = level },
                                    label = { Text(level.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Re-enhance button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    error = null
                                    val result = onEnhance(EnhancementRequest(
                                        originalPrompt = originalText,
                                        targetAI = selectedTarget,
                                        level = selectedLevel
                                    ))
                                    result.fold(
                                        onSuccess = { enhancedText = it },
                                        onFailure = { error = it.message }
                                    )
                                    isLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Re-enhance with Options")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Result area
                Text(
                    text = "Enhanced:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Enhancing...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        error != null -> {
                            Text(
                                text = "Error: $error",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        enhancedText != null -> {
                            Text(
                                text = enhancedText!!,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { enhancedText?.let { onCopy(it) } },
                        modifier = Modifier.weight(1f),
                        enabled = enhancedText != null && !isLoading
                    ) {
                        Text("📋 Copy & Close")
                    }
                }
            }
        }
    }
}
