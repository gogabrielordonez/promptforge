package com.adwaizer.promptforge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Custom colors - Forge theme (warm metallics + spark accent)
private val ForgeOrange = Color(0xFFFF6B35)
private val ForgeGold = Color(0xFFFFB347)
private val ForgeDark = Color(0xFF1A1A2E)
private val ForgeCharcoal = Color(0xFF16213E)
private val ForgeSteel = Color(0xFF0F3460)
private val SparkYellow = Color(0xFFFFD93D)

private val DarkColorScheme = darkColorScheme(
    primary = ForgeOrange,
    onPrimary = Color.White,
    primaryContainer = ForgeSteel,
    onPrimaryContainer = ForgeGold,
    secondary = SparkYellow,
    onSecondary = ForgeDark,
    secondaryContainer = ForgeCharcoal,
    onSecondaryContainer = SparkYellow,
    tertiary = ForgeGold,
    onTertiary = ForgeDark,
    background = ForgeDark,
    onBackground = Color.White,
    surface = ForgeCharcoal,
    onSurface = Color.White,
    surfaceVariant = ForgeSteel,
    onSurfaceVariant = Color(0xFFB8C4CE),
    error = Color(0xFFFF6B6B),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ForgeOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4D6),
    onPrimaryContainer = ForgeOrange,
    secondary = Color(0xFFE67E22),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Color(0xFFE67E22),
    tertiary = ForgeGold,
    onTertiary = ForgeDark,
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun PromptForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Typography
private val Typography = Typography(
    // Use default Material 3 typography
    // Customize here if needed
)
