package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes

interface Switcher {
    // A [tap] waits even right after a wait timed out.
    suspend fun connect(tap: Boolean = false): Boolean

    suspend fun set(enabled: Boolean, changes: Changes): Changes
}
