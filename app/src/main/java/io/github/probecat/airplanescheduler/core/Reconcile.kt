package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.Boundary.Step
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine.Device
import io.github.probecat.airplanescheduler.core.Engine.Effect
import io.github.probecat.airplanescheduler.core.Engine.Event
import io.github.probecat.airplanescheduler.core.Engine.Result
import io.github.probecat.airplanescheduler.core.Engine.State
import io.github.probecat.airplanescheduler.core.Engine.Then
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// What the app does with Strict behavior. Starts work as with Conservative, and nothing switches during a window. The
// end of a window that the app started switches to what the schedule asks for, whatever was set before the start and
// whoever switched it. A window that the app has no start of, as for a second alarm, ends as with Conservative. A
// start or end that the app could not switch, or that passed while the phone was off, is owed. Once the app can switch
// again, it switches to what the schedules ask for now. A radio that the user switched since then stays as the user
// left it.
object Reconcile {
    private val AIRPLANE = Changes(airplane = true, wifi = false)

    // [seen] is the device when the switch became owed. [touched] holds the radios that the user switched since, for
    // when the user switched one back. [ended] is the window that an owed end ends, and [schedule] the id of its
    // schedule, which an early end leaves skipped or off.
    data class Owed(
        val seen: Device,
        val touched: Changes = Changes.NONE,
        val ended: Started? = null,
        val schedule: Long? = null,
    ) {
        fun touched(device: Device) =
            touched + Changes(device.airplaneOn != seen.airplaneOn, device.wifiOn != seen.wifiOn)
    }

    fun on(state: State, event: Event, device: Device): Result = when (event) {
        is Event.Alarm -> {
            val started = state.started
            if (ScheduleResolver.window(event.schedules, event.now) != null || started == null) {
                conservative(state, event, device)
            } else {
                end(state, started, event.schedules, event.now, device)
            }
        }
        is Event.Edit -> edit(state, event, device)
        is Event.CatchUp -> conservative(state, event, device)
        is Event.Reset -> reset(state, event.schedules, event.now, device)
        is Event.Ready -> ready(state, event.schedules, event.now, device)
        // Android delivers changes to a background app late and merged, so airplane mode switched off and on again
        // can look unchanged.
        Event.Touched -> Result(state.copy(owed = state.owed?.let { it.copy(touched = it.touched(device) + AIRPLANE) }))
        is Event.Switched -> switched(state, event, device)
    }

    // A new start or end replaces what the app owed.
    private fun conservative(state: State, event: Event, device: Device): Result {
        val result = Engine.on(state, event, device)
        if (result.effects.none { it is Effect.Switch }) return result
        return result.copy(state = result.state.copy(owed = null))
    }

    private fun edit(state: State, event: Event.Edit, device: Device): Result {
        val step = Boundary.atEdit(event.old, event.new, event.now, state.started)
        if (step !is Step.End) return conservative(state, event, device)
        val before = checkNotNull(ScheduleResolver.window(event.old, event.now))
        val running = state.started?.takeIf { it.window == before.start } ?: return conservative(state, event, device)
        val kept = state.wifiKeptUntil?.isAfter(event.now) == true
        val next = State(wifiKeptUntil = Boundary.keptWifi(running, device.wifiOn, step.until, kept))
        return Result(next, listOf(switch(running, checkNotNull(step.schedule), device, device, natural = false)))
    }

    // Starts a window that the app missed while the phone was off. An end that passed meanwhile is caught up.
    private fun reset(state: State, schedules: List<Schedule>, now: LocalDateTime, device: Device): Result {
        val started = state.started
        val window = ScheduleResolver.window(schedules, now)
        return when {
            window != null && started?.window == window.start -> Result(state)
            window != null -> conservative(state, Event.CatchUp(schedules, now), device)
            started == null -> Result(state)
            else -> end(state, started, schedules, now, device)
        }
    }

    private fun ready(state: State, schedules: List<Schedule>, now: LocalDateTime, device: Device): Result {
        val owed = state.owed ?: return Result(state)
        val touched = owed.touched(device)
        val cleared = state.copy(owed = null)
        val window = ScheduleResolver.window(schedules, now)
        if (window != null) {
            val settled = Device(device.airplaneOn || touched.airplane, device.wifiOn && !touched.wifi)
            return Engine.step(cleared, Step.Start(window), schedules, now, settled)
        }
        val settled = Device(device.airplaneOn && !touched.airplane, device.wifiOn || touched.wifi)
        val started = owed.ended ?: state.started ?: return Result(cleared)
        return end(cleared, started, schedules, now, device, settled, schedules.find { it.id == owed.schedule })
    }

    // [settled] counts the radios that the user switched as already set as the end wants them. [owedBy] is the
    // schedule of an owed end, which an early end leaves skipped or off.
    private fun end(
        state: State,
        started: Started,
        schedules: List<Schedule>,
        now: LocalDateTime,
        device: Device,
        settled: Device = device,
        owedBy: Schedule? = null,
    ): Result {
        val ended = ScheduleResolver.lastEnded(schedules, now)?.takeIf { it.window == started.window }
        val schedule = owedBy ?: ended?.schedule ?: ScheduleResolver.activeSchedule(schedules, started.window)
            // A schedule that is gone has no end options to switch to.
            ?: return Result(state.copy(started = null, owed = null), Engine.forget(started, schedules, device))
        return Result(State(), listOf(switch(started, schedule, device, settled, natural = true)))
    }

    // Nothing stays as it was before the start, so the report does not say so. [wanted] keeps whether the start meant
    // Wi-Fi off, also for when this end is owed.
    private fun switch(
        started: Started,
        schedule: Schedule,
        device: Device,
        settled: Device,
        natural: Boolean,
    ): Effect.Switch {
        val wifiOff = started.wanted.wifi || started.already.wifi
        val record = started.copy(wanted = started.wanted.copy(wifi = wifiOff), already = Changes.NONE)
        val changes = Changes(settled.airplaneOn, wifiOff && schedule.enableWifi && !settled.wifiOn)
        return Effect.Switch(false, changes, Then.End(record, schedule, device, natural))
    }

    // A switch that failed is owed, and one that worked settles what was owed. A switch that finishes after the next
    // start or end leaves what that one owes.
    private fun switched(state: State, event: Event.Switched, device: Device): Result {
        val result = Engine.switched(state, event.switch, event.made)
        val failed = !(event.switch.changes - event.made).isEmpty
        val owed = when (val then = event.switch.then) {
            Then.Start -> if (state.started == null) return result else Owed(device).takeIf { failed }
            is Then.End -> if (state.started != null) {
                return result
            } else {
                Owed(device, ended = then.started, schedule = then.schedule.id).takeIf { failed }
            }
            Then.Undo -> return result
        }
        return result.copy(state = result.state.copy(owed = owed))
    }
}
