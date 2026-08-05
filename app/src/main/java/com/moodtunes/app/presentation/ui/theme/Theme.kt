package com.moodtunes.app.presentation.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EuphoricAccent,
    onPrimary = White,
    primaryContainer = Color(0xFF2D1040),
    onPrimaryContainer = EuphoricAccent,
    secondary = CalmAccent,
    onSecondary = White,
    secondaryContainer = Color(0xFF0D2E2C),
    onSecondaryContainer = CalmAccent,
    tertiary = HappyAccent,
    onTertiary = Black,
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFEEEEF4),
    surface = Color(0xFF12121A),
    onSurface = Color(0xFFCCCCDD),
    surfaceVariant = Color(0xFF1E1E2A),
    onSurfaceVariant = Color(0xFF8888AA),
    outline = Color(0xFF2E2E40),
    outlineVariant = Color(0xFF2A2A3A),
    error = FavoriteRed,
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = EuphoricGradientStart,
    onPrimary = White,
    primaryContainer = Color(0xFFF3E5F5),
    onPrimaryContainer = Color(0xFF4A148C),
    secondary = CalmGradientStart,
    onSecondary = White,
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = HappyGradientStart,
    onTertiary = White,
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF181820),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181820),
    surfaceVariant = Color(0xFFEEEEF3),
    onSurfaceVariant = Color(0xFF555566),
    outline = Color(0xFFD8D8E5),
    outlineVariant = Color(0xFFE5E5F0),
    error = FavoriteRed,
    onError = White
)

@Composable
fun MoodTunesTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
