package io.github.probecat.airplanescheduler.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.probecat.airplanescheduler.R

// Cut down to Latin, with width and slant fixed. Optical size ranges 11 to 46, and roundness 0 to 100.
private fun googleSansFlex(vararg settings: FontVariation.Setting) = FontFamily(
    (100..700 step 50).map { weight ->
        Font(
            R.font.google_sans_flex,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight), *settings),
        )
    },
)

private fun TextStyle.sansFlex() = copy(fontFamily = googleSansFlex(FontVariation.opticalSizing(fontSize)))

// Display styles only show times.
private fun TextStyle.time() = copy(
    fontFamily = googleSansFlex(FontVariation.opticalSizing(fontSize), FontVariation.Setting("ROND", 100f)),
)

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
