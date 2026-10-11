package io.github.probecat.airplanescheduler.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.probecat.airplanescheduler.R

// Google Sans Flex, cut down to Latin with width, roundness and slant fixed at their defaults. Other
// scripts fall back to the system font.
private val GoogleSansFlex = FontFamily(
    (100..700 step 50).map { weight ->
        Font(
            R.font.google_sans_flex,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private fun TextStyle.sansFlex() = copy(fontFamily = GoogleSansFlex)

// Display styles only show times, so their digits keep one width and times line up across cards.
private fun TextStyle.time() = sansFlex().copy(fontFeatureSettings = "tnum")

internal val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.time(),
        displayMedium = displayMedium.time(),
        displaySmall = displaySmall.time(),
        headlineLarge = headlineLarge.sansFlex(),
        headlineMedium = headlineMedium.sansFlex(),
        headlineSmall = headlineSmall.sansFlex(),
        titleLarge = titleLarge.sansFlex(),
        titleMedium = titleMedium.sansFlex(),
        titleSmall = titleSmall.sansFlex(),
        bodyLarge = bodyLarge.sansFlex(),
        bodyMedium = bodyMedium.sansFlex(),
        bodySmall = bodySmall.sansFlex(),
        labelLarge = labelLarge.sansFlex(),
        labelMedium = labelMedium.sansFlex(),
        labelSmall = labelSmall.sansFlex(),
    )
}
