package com.adwaizer.promptforge

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adwaizer.promptforge.core.PromptForgeService
import com.adwaizer.promptforge.model.*
import com.adwaizer.promptforge.ui.FloatingWidgetService
import com.adwaizer.promptforge.ui.theme.PromptForgeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var promptForgeService: PromptForgeService? = null
    private var isBound by mutableStateOf(false)

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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Handle result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Start and bind to service
        PromptForgeService.startService(this)
        Intent(this, PromptForgeService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            PromptForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PromptForgeApp(
                        isBound = isBound,
                        onEnhance = { request ->
                            promptForgeService?.enhance(request)
                        },
                        onToggleWidget = { enabled ->
                            if (enabled) {
                                requestOverlayPermission()
                            } else {
                                FloatingWidgetService.hide(this)
                            }
                        },
                        onOpenSettings = {
                            // Open app settings
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            FloatingWidgetService.show(this)
        } else {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptForgeApp(
    isBound: Boolean,
    onEnhance: suspend (EnhancementRequest) -> Result<EnhancementResult>?,
    onToggleWidget: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var enhancedText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTarget by remember { mutableStateOf(TargetAI.GENERIC) }
    var selectedLevel by remember { mutableStateOf(EnhancementLevel.BALANCED) }
    var showTemplates by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ PromptForge")
                        if (!isBound) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleWidget(true) }) {
                        Icon(Icons.Default.PictureInPicture, "Widget")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Edit, "Enhance") },
                    label = { Text("Enhance") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.LibraryBooks, "Templates") },
                    label = { Text("Templates") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.History, "History") },
                    label = { Text("History") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> EnhanceTab(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    enhancedText = enhancedText,
                    isLoading = isLoading,
                    selectedTarget = selectedTarget,
                    onTargetChange = { selectedTarget = it },
                    selectedLevel = selectedLevel,
                    onLevelChange = { selectedLevel = it },
                    isBound = isBound,
                    onEnhance = {
                        scope.launch {
                            isLoading = true
                            val result = onEnhance(
                                EnhancementRequest(
                                    originalPrompt = inputText,
                                    targetAI = selectedTarget,
                                    level = selectedLevel
                                )
                            )
                            result?.fold(
                                onSuccess = { enhancedText = it.enhancedPrompt },
                                onFailure = { /* Handle error */ }
                            )
                            isLoading = false
                        }
                    },
                    onCopy = {
                        enhancedText?.let {
                            clipboardManager.setText(AnnotatedString(it))
                        }
                    }
                )
                1 -> TemplatesTab(
                    onSelectTemplate = { template ->
                        inputText = template.basePrompt
                        selectedTab = 0
                    }
                )
                2 -> HistoryTab()
            }
        }
    }
}

@Composable
fun EnhanceTab(
    inputText: String,
    onInputChange: (String) -> Unit,
    enhancedText: String?,
    isLoading: Boolean,
    selectedTarget: TargetAI,
    onTargetChange: (TargetAI) -> Unit,
    selectedLevel: EnhancementLevel,
    onLevelChange: (EnhancementLevel) -> Unit,
    isBound: Boolean,
    onEnhance: () -> Unit,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Input field
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Enter your prompt") },
            placeholder = { Text("Write a cover letter for...") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Target AI selector
        Text("Target AI:", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TargetAI.values().forEach { target ->
                FilterChip(
                    selected = selectedTarget == target,
                    onClick = { onTargetChange(target) },
                    label = { Text(target.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Level selector
        Text("Enhancement Level:", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EnhancementLevel.values().forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { onLevelChange(level) },
                    label = { Text(level.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhance button
        Button(
            onClick = onEnhance,
            modifier = Modifier.fillMaxWidth(),
            enabled = isBound && inputText.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enhancing...")
            } else {
                Text("✨ Enhance Prompt")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Result area
        AnimatedVisibility(visible = enhancedText != null) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Enhanced Prompt:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        text = enhancedText ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TemplatesTab(onSelectTemplate: (Template) -> Unit) {
    val templates = remember {
        com.adwaizer.promptforge.core.EnhancementEngine.BUILT_IN_TEMPLATES
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(templates) { template ->
            Card(
                onClick = { onSelectTemplate(template) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(template.category.displayName) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTab() {
    // Placeholder for history implementation
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.History,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Enhancement history will appear here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
