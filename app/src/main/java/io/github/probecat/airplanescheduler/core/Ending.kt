package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes

data class Ending(
    // What the end meant to undo but couldn't.
    val failed: Changes,
    // Airplane mode never turned on and is off, so there was nothing to undo.
    val missedStart: Boolean = false,
    // Airplane mode was on before the start, so it stays on.
    val airplaneWasOn: Boolean = false,
    val airplaneTurnedOff: Boolean = false,
    // The end didn't turn airplane mode off, and it's on, as when it was on before the start.
    val airplaneStaysOn: Boolean = false,
    // Wi-Fi was off before the start, so it stays off although the schedule turns it back on.
    val wifiWasOff: Boolean = false,
    // The app turned Wi-Fi off, and the schedule keeps it off.
    val wifiKeptOff: Boolean = false,
    val wifiBackOn: Boolean = false,
) {
    val clean: Boolean
        get() = failed.isEmpty && !missedStart && !airplaneTurnedOff && !airplaneStaysOn && !wifiWasOff

    companion object {
        // [airplaneOn] and [wifiOn] are read just before the end.
        fun of(
            started: Started,
            undo: Changes,
            made: Changes,
            airplaneOn: Boolean,
            wifiOn: Boolean,
            enableWifi: Boolean,
        ) = Ending(
            failed = undo - made,
            missedStart = started.wanted.airplane && !started.changes.airplane && !airplaneOn,
            airplaneWasOn = started.already.airplane && airplaneOn,
            airplaneTurnedOff = started.changes.airplane && !airplaneOn,
            airplaneStaysOn = airplaneOn && !made.airplane,
            wifiWasOff = started.already.wifi && enableWifi && !wifiOn,
            wifiKeptOff = started.changes.wifi && !enableWifi,
            wifiBackOn = made.wifi,
        )
    }
}
