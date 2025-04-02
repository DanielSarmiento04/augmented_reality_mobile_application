package com.example.augmented_mobile_application.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrightGreen,
    onPrimary = OffWhite,
    primaryContainer = DarkGreen,
    onPrimaryContainer = LightGreen,
    secondary = LightGreen,
    onSecondary = DarkGray,
    background = DarkGray,
    onBackground = OffWhite,
    surface = DarkGray,
    onSurface = OffWhite,
    error = Pink80,
    onError = OffWhite
)

private val LightColorScheme = lightColorScheme(
    primary = DarkGreen,
    onPrimary = OffWhite,
    primaryContainer = LightGreen,
    onPrimaryContainer = DarkGreen,
    secondary = BrightGreen,
    onSecondary = OffWhite,
    background = OffWhite,
    onBackground = DarkGray,
    surface = OffWhite,
    onSurface = DarkGray,
    error = Pink40,
    onError = OffWhite
)

@Composable
fun Augmented_mobile_applicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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

    // Apply the status bar color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}