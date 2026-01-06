package com.spendmanager.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Clean aesthetic white color scheme
private val LightColorScheme = lightColorScheme(
    // Primary - Charcoal for main actions
    primary = Charcoal,
    onPrimary = White,
    primaryContainer = Gray100,
    onPrimaryContainer = Charcoal,

    // Secondary - Muted tones
    secondary = CharcoalMuted,
    onSecondary = White,
    secondaryContainer = Gray200,
    onSecondaryContainer = Charcoal,

    // Tertiary - Accent blue
    tertiary = AccentBlue,
    onTertiary = White,
    tertiaryContainer = AccentBlueLight,
    onTertiaryContainer = AccentBlue,

    // Error - Subtle red
    error = AccentRed,
    errorContainer = AccentRedLight,
    onError = White,
    onErrorContainer = AccentRed,

    // Background - Pure white
    background = White,
    onBackground = Charcoal,

    // Surface - Clean whites
    surface = White,
    onSurface = Charcoal,
    surfaceVariant = OffWhite,
    onSurfaceVariant = CharcoalMuted,

    // Outline - Subtle borders
    outline = Gray300,
    outlineVariant = Gray200,

    // Inverse
    inverseSurface = Charcoal,
    inverseOnSurface = White,
    inversePrimary = Gray400,

    // Surface tones
    surfaceTint = Charcoal,
)

// Dark theme (keeping for system dark mode support)
private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Charcoal,
    primaryContainer = CharcoalLight,
    onPrimaryContainer = White,

    secondary = Gray400,
    onSecondary = Charcoal,
    secondaryContainer = Gray800,
    onSecondaryContainer = Gray200,

    tertiary = AccentBlueLight,
    onTertiary = Charcoal,
    tertiaryContainer = AccentBlue,
    onTertiaryContainer = White,

    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF121212),
    onBackground = White,
    surface = Color(0xFF121212),
    onSurface = White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Gray400,

    outline = Gray600,
    outlineVariant = Gray700,

    inverseSurface = White,
    inverseOnSurface = Charcoal,
    inversePrimary = Charcoal,
)

@Composable
fun SpendManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color for consistent aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // White status bar for light theme
            window.statusBarColor = if (darkTheme) Color(0xFF121212).toArgb() else White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
