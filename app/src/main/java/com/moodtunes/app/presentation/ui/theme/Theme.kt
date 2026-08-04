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
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = CardBorder,
    outlineVariant = DividerColor,
    error = FavoriteRed,
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = EuphoricGradientStart,
    onPrimary = White,
    secondary = CalmGradientStart,
    onSecondary = White,
    tertiary = HappyGradientStart,
    onTertiary = White,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1A1A1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1E),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF444455),
    outline = Color(0xFFD0D0DD),
    error = FavoriteRed,
    onError = White
)

@Composable
fun MoodTunesTheme(
    darkTheme: Boolean = true, // Defaults to dark theme for immersive music player experience
    dynamicColor: Boolean = true, // Material You dynamic colors on Android 12+ (API 31+)
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
            // statusBarColor / navigationBarColor are deprecated on API 35+.
            // enableEdgeToEdge() in MainActivity handles them on API 35+.
            // Keep this for API 26–34 backward compatibility.
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

