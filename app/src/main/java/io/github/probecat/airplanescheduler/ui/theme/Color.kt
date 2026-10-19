package io.github.probecat.airplanescheduler.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Tonal spot schemes from the icon's indigo (#4A5AA8), used when dynamic color is off.
internal val SeedLight = lightColorScheme(
    primary = Color(0xFF505B92),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF384379),
    inversePrimary = Color(0xFFB9C3FF),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF434659),
    tertiary = Color(0xFF76546E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F2),
    onTertiaryContainer = Color(0xFF5C3C55),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceTint = Color(0xFF505B92),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDBD9E0),
)
internal val SeedDark = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF212C61),
    primaryContainer = Color(0xFF384379),
    onPrimaryContainer = Color(0xFFDEE1FF),
    inversePrimary = Color(0xFF505B92),
    secondary = Color(0xFFC3C5DD),
    onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFE5BAD8),
    onTertiary = Color(0xFF44263E),
    tertiaryContainer = Color(0xFF5C3C55),
    onTertiaryContainer = Color(0xFFFFD7F2),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceTint = Color(0xFFB9C3FF),
    inverseSurface = Color(0xFFE3E1E9),
    inverseOnSurface = Color(0xFF303036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceDim = Color(0xFF121318),
)

// Fixed in every theme so the add button stands out from switched-on schedules, which use the primary
// container.
val AddButton = Color(0xFFE4CEFF)
val OnAddButton = Color(0xFF54436B)

// Material has no warning role, so a fixed orange stands in for problems that only delay schedules.
data class WarningColors(val container: Color, val onContainer: Color)

internal val WarningLight = WarningColors(Color(0xFFFFDDB8), Color(0xFF653E00))
internal val WarningDark = WarningColors(Color(0xFF653E00), Color(0xFFFFDDB8))

// Material has no success role either; a fixed green pairs with the error red for finished steps.
internal val SuccessLight = Color(0xFF006E1C)
internal val SuccessDark = Color(0xFFA3D39C)
