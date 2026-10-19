package io.github.probecat.airplanescheduler.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.probecat.airplanescheduler.data.ThemeMode

@Composable
fun isDark(theme: ThemeMode): Boolean = when (theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.BLACK -> true
}

val LocalWarningColors = staticCompositionLocalOf { WarningLight }

// Android 12 added the wallpaper colors.
val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AppTheme(theme: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = isDark(theme)
    val context = LocalContext.current
    val scheme = when {
        hasDynamicColor && dynamicColor && dark -> dynamicDarkColorScheme(context)
        hasDynamicColor && dynamicColor -> dynamicLightColorScheme(context)
        dark -> SeedDark
        else -> SeedLight
    }
    CompositionLocalProvider(
        LocalWarningColors provides if (dark) WarningDark else WarningLight,
    ) {
        MaterialTheme(
            colorScheme = if (theme == ThemeMode.BLACK) scheme.black() else scheme,
            typography = AppTypography,
            content = content,
        )
    }
}

// Pure black behind everything; containers keep their dark tones so cards stay visible.
private fun ColorScheme.black(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)
