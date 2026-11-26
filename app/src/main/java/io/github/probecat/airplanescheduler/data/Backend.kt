package io.github.probecat.airplanescheduler.data

import androidx.annotation.StringRes
import io.github.probecat.airplanescheduler.R

// The access provider in Settings. Saved by name, so the entries keep their names.
enum class Backend(@StringRes val label: Int) {
    SHIZUKU(R.string.backend_shizuku),
    ROOT(R.string.backend_root),
}
