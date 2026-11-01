package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes

// Switches airplane mode and Wi-Fi with privileges the app doesn't have.
interface Switcher {
    // Waits a moment for the privileged side when a fresh process may not have reached it yet, and says
    // whether it can be reached. A [tap] waits even when a wait just ran out.
    suspend fun connect(tap: Boolean = false): Boolean

    // Turns [changes] on, or back off, and returns the ones that worked.
    suspend fun set(enabled: Boolean, changes: Changes): Changes
}
