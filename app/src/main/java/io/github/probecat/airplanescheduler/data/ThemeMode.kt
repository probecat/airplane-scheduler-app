package io.github.probecat.airplanescheduler.data

import androidx.annotation.StringRes
import io.github.probecat.airplanescheduler.R

enum class ThemeMode(@StringRes val label: Int) {
    SYSTEM(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    BLACK(R.string.theme_black),
}
