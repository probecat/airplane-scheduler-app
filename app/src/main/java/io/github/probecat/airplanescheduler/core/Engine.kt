package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.Boundary.Step
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// A switch finishes later with [Event.Switched]. Other events can come first, for example when the user ends a window
// while its start is still switching.
object Engine {
    // [started] is the window that the app started. [wifiKeptUntil] is the time until which Wi-Fi stays on after the
    // user turned it on against a start.
    data class State(val started: Started? = null, val wifiKeptUntil: LocalDateTime? = null)

    data class Device(val airplaneOn: Boolean, val wifiOn: Boolean)

    sealed interface Event {
        // A start or an end at its planned time. The engine reports the end even when it went as planned.
        data class Alarm(val schedules: List<Schedule>, val now: LocalDateTime) : Event

        data class Edit(val old: List<Schedule>, val new: List<Schedule>, val now: LocalDateTime) : Event

        data class CatchUp(val schedules: List<Schedule>, val now: LocalDateTime) : Event

        // After a boot or a clock change, the engine forgets a window whose end passed in the meantime.
        data class Reset(val schedules: List<Schedule>, val now: LocalDateTime) : Event

        data class Switched(val switch: Effect.Switch, val made: Changes) : Event
    }

    sealed interface Effect {
        data class Switch(val enabled: Boolean, val changes: Changes, val then: Then) : Effect

        data class Report(val schedule: Schedule, val ending: Ending) : Effect

        data class MissedEnd(val schedule: Schedule?, val left: Changes) : Effect
    }

    sealed interface Then {
        data object Start : Then

        data class End(val started: Started, val schedule: Schedule, val before: Device, val natural: Boolean) : Then

        // Undoes what a start switched when the switch finished after its window ended.
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
                    listOf(
                        Effect.Switch(
                            false,
                            Boundary.takeOver(step.changes, step.schedule, device.airplaneOn),
                            Then.End(started, step.schedule, device, natural),
                        ),
                    ),
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
