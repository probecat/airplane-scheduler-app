package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes

interface Switcher {
    fun access(): Access

    // False when the app can't ask, so the user has to allow it in the other app.
    suspend fun request(): Boolean

    // A [tap] waits even right after a wait timed out.
    suspend fun connect(tap: Boolean = false): Boolean

    suspend fun set(enabled: Boolean, changes: Changes): Changes
}
