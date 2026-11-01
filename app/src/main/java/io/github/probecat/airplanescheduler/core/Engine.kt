package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.Boundary.Step
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// The app's one state machine. Each event gives the next state and what to do about it. A switch
// finishes later with [Event.Switched], and other events may come first, as when a window is ended
// while its start is still switching.
object Engine {
    // The window the app started, and until when Wi-Fi turned on against a start stays on.
    data class State(val started: Started? = null, val wifiKeptUntil: LocalDateTime? = null)

    // Read just before each event.
    data class Device(val airplaneOn: Boolean, val wifiOn: Boolean)

    sealed interface Event {
        // A start or end at its time. Its end is reported even when it went as planned.
        data class Alarm(val schedules: List<Schedule>, val now: LocalDateTime) : Event

        data class Edit(val old: List<Schedule>, val new: List<Schedule>, val now: LocalDateTime) : Event

        // Starts the running window by hand after the app missed its start.
        data class CatchUp(val schedules: List<Schedule>, val now: LocalDateTime) : Event

        // After a boot or clock change, a window whose end passed meanwhile is forgotten.
        data class Reset(val schedules: List<Schedule>, val now: LocalDateTime) : Event

        data class Switched(val switch: Effect.Switch, val made: Changes) : Event
    }

    sealed interface Effect {
        // Answered with [Event.Switched] and what worked.
        data class Switch(val enabled: Boolean, val changes: Changes, val then: Then) : Effect

        data class Report(val schedule: Schedule, val ending: Ending) : Effect

        // What a forgotten window left switched, which is reported rather than undone.
        data class MissedEnd(val schedule: Schedule?, val left: Changes) : Effect
    }

    // What a switch finishes.
    sealed interface Then {
        data object Start : Then

        data class End(val started: Started, val schedule: Schedule, val before: Device, val natural: Boolean) : Then

        // Turns back what a start made after its window ended.
        data object Undo : Then
    }

    data class Result(val state: State, val effects: List<Effect> = emptyList())

    fun on(state: State, event: Event, device: Device): Result = when (event) {
        is Event.Alarm -> step(
            state,
            Boundary.atAlarm(event.schedules, event.now, state.started),
            event.schedules,
            event.now,
            device,
            natural = true,
        )
        is Event.Edit ->
            step(state, Boundary.atEdit(event.old, event.new, event.now, state.started), event.new, event.now, device)
        is Event.CatchUp -> ScheduleResolver.window(event.schedules, event.now)
            ?.let { step(state, Step.Start(it), event.schedules, event.now, device) } ?: Result(state)
        is Event.Reset -> {
            val started = state.started
            if (started == null || ScheduleResolver.window(event.schedules, event.now)?.start == started.window) {
                Result(state)
            } else {
                Result(state.copy(started = null), forget(started, event.schedules, device))
            }
        }
        is Event.Switched -> switched(state, event.switch, event.made)
    }

    private fun step(
        state: State,
        step: Step,
        schedules: List<Schedule>,
        now: LocalDateTime,
        device: Device,
        natural: Boolean = false,
    ): Result = when (step) {
        is Step.Start -> {
            val kept = state.wifiKeptUntil?.isAfter(now) == true
            val (started, wanted) = Boundary.begin(step.window, state.started, device.airplaneOn, device.wifiOn, kept)
            Result(state.copy(started = started), listOf(Effect.Switch(true, wanted, Then.Start)))
        }
        is Step.End -> {
            val started = state.started
            val kept = state.wifiKeptUntil?.isAfter(now) == true
            val next =
                State(started = null, wifiKeptUntil = Boundary.keptWifi(started, device.wifiOn, step.until, kept))
            when {
                started == null -> Result(next)
                step.schedule == null -> Result(next, forget(started, schedules, device))
                else -> Result(
                    next,
                    listOf(Effect.Switch(false, step.changes, Then.End(started, step.schedule, device, natural))),
                )
            }
        }
        is Step.Move -> Result(state.copy(started = step.started))
        Step.None -> Result(state)
    }

    private fun switched(state: State, switch: Effect.Switch, made: Changes): Result = when (val then = switch.then) {
        Then.Start -> state.started?.let { Result(state.copy(started = it.copy(changes = it.changes + made))) }
            ?: Result(state, listOf(Effect.Switch(false, made, Then.Undo)))
        is Then.End -> {
            val before = then.before
            val ending = Ending.of(
                then.started,
                switch.changes,
                made,
                before.airplaneOn,
                before.wifiOn,
                then.schedule.enableWifi,
            )
            val reported = then.natural || !ending.failed.isEmpty
            Result(state, listOfNotNull(Effect.Report(then.schedule, ending).takeIf { reported }))
        }
        Then.Undo -> Result(state)
    }

    private fun forget(started: Started, schedules: List<Schedule>, device: Device): List<Effect> {
        val schedule = ScheduleResolver.activeSchedule(schedules, started.window)
        val left = Boundary.leftSwitched(started, schedule?.enableWifi ?: true, device.airplaneOn, device.wifiOn)
        return listOfNotNull(Effect.MissedEnd(schedule, left).takeIf { !left.isEmpty })
    }
}
