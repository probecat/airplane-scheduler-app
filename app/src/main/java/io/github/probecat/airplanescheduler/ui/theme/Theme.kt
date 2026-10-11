package io.github.probecat.airplanescheduler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.probecat.airplanescheduler.data.ThemeMode

@Composable
fun isDark(theme: ThemeMode): Boolean = when (theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.BLACK -> true
}

@Composable
fun AppTheme(theme: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isDark(theme)
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> SeedDark
        else -> SeedLight
    }
    MaterialTheme(
        colorScheme = if (theme == ThemeMode.BLACK) scheme.black() else scheme,
        typography = AppTypography,
        content = content,
    )
}

// Pure black behind everything; containers keep their dark tones so cards stay visible.
private fun ColorScheme.black(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)
