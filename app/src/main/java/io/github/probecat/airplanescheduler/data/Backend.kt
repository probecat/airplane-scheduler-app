package io.github.probecat.airplanescheduler.data

import androidx.annotation.StringRes
import io.github.probecat.airplanescheduler.R

enum class Backend(@StringRes val label: Int) {
    SHIZUKU(R.string.backend_shizuku),
    ROOT(R.string.backend_root),
}
