package io.github.probecat.airplanescheduler.system

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Ending
import io.github.probecat.airplanescheduler.core.Engine
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// All work runs on the main thread, so events take turns between switches.
object AirplaneController {
    suspend fun onAlarm(app: App, now: LocalDateTime) {
        val event = Engine.Event.Alarm(app.schedules.all, now)
        ScheduleUpdater.expire(app, now)
        dispatch(app, event)
    }

    // A window whose end passed while the phone was off, or that a clock change skipped, is forgotten.
    suspend fun forgetMissedEnd(app: App, now: LocalDateTime) =
        dispatch(app, Engine.Event.Reset(app.schedules.all, now))

    suspend fun dispatch(app: App, event: Engine.Event) {
        holding {
            val result = Engine.on(app.engine.state, event, device(app))
            app.engine.state = result.state
            if (event is Engine.Event.Alarm && result.effects.isEmpty()) app.log.add("No switch needed")
            for (effect in result.effects) perform(app, effect)
        }
    }

    private suspend fun perform(app: App, effect: Engine.Effect) {
        when (effect) {
            is Engine.Effect.Switch -> {
                val made = app.switcher.set(effect.enabled, effect.changes)
                logSwitch(app, Events.then(effect.then), effect.enabled, effect.changes, made)
                dispatch(app, Engine.Event.Switched(effect, made))
            }
            is Engine.Effect.Report -> Notifier.ended(app, effect.schedule, effect.ending)
            is Engine.Effect.MissedEnd -> {
                val schedule = effect.schedule?.let { "#${it.id}" } ?: "a deleted schedule"
                app.log.add("Missed END of $schedule and left switched: ${Events.changes(effect.left)}")
                Notifier.missedEnd(app, effect.schedule, effect.left)
            }
        }
    }

    suspend fun undo(app: App, changes: Changes, report: Notifier.Report) {
        val made = holding { app.switcher.set(false, changes) }
        logSwitch(app, "RETRY #${report.id}", false, changes, made)
        Notifier.ended(app, report, Ending.retried(changes, made, airplaneOn(app)))
    }

    private fun logSwitch(app: App, what: String, enabled: Boolean, wanted: Changes, made: Changes) {
        app.log.add("$what via ${app.access.value.kind.name}: ${Events.switched(enabled, wanted, made)}")
    }

    fun device(context: Context) = Engine.Device(airplaneOn(context), wifiOn(context))

    // Any app can read both states. Only a change needs privileges.
    fun airplaneOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun wifiOn(context: Context): Boolean = context.getSystemService(WifiManager::class.java).isWifiEnabled

    fun missedSchedule(app: App, now: LocalDateTime): Schedule? =
        Boundary.missed(app.schedules.all, now, app.engine.state.started, airplaneOn(app), switching)

    private val inFlight = MutableStateFlow(0)

    val inFlightCount: StateFlow<Int> = inFlight.asStateFlow()

    val switching: Boolean get() = inFlight.value > 0

    // The hold starts now, so the time before [block] runs does not look like a missed start either.
    fun launchHolding(app: App, block: suspend () -> Unit, then: () -> Unit): Job {
        inFlight.update { it + 1 }
        return app.launch {
            try {
                block()
            } finally {
                inFlight.update { it - 1 }
            }
            then()
        }
    }

    // Counts as switching while [block] runs, so states during a switch do not look like a missed start.
    suspend fun <T> holding(block: suspend () -> T): T {
        inFlight.update { it + 1 }
        try {
            return block()
        } finally {
            inFlight.update { it - 1 }
        }
    }
}
