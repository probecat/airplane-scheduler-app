package io.github.probecat.airplanescheduler.system

enum class AccessState { READY, OFFLINE, PERMISSION_NEEDED }

data class Access(val kind: Kind, val state: AccessState) {
    enum class Kind { SHIZUKU, SUI, ROOT }

    val ready: Boolean get() = state == AccessState.READY
}
