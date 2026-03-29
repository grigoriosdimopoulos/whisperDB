package com.whisperlm.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = PurplePrimary,
    onPrimary        = OnPurple,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurpleLight,
    secondary        = PurpleLight,
    onSecondary      = OnPurple,
    secondaryContainer = PurpleDark,
    onSecondaryContainer = PurpleLight,
    background       = BackgroundDark,
    onBackground     = OnSurfaceDark,
    surface          = SurfaceDark,
    onSurface        = OnSurfaceDark,
    surfaceVariant   = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error            = ErrorRed,
    outline          = GrayMid
)

private val LightColorScheme = lightColorScheme(
    primary          = PurplePrimary,
    onPrimary        = OnPurple,
    primaryContainer = SurfaceVariantLight,
    onPrimaryContainer = PurpleDark,
    secondary        = PurpleDark,
    onSecondary      = OnPurple,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = PurpleDark,
    background       = BackgroundLight,
    onBackground     = OnSurfaceLight,
    surface          = SurfaceLight,
    onSurface        = OnSurfaceLight,
    surfaceVariant   = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error            = ErrorRed,
    outline          = GrayMid
)

@Composable
fun WhisperLMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WhisperLMTypography,
        content = content
    )
}
