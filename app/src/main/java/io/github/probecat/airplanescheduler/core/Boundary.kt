package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// What the app does when a window starts or ends. It undoes only what it changed itself, and it
// skips a start or end that it missed instead of catching up later.
object Boundary {
    // The last window that the app started. [window] is the start of its chain. [changes] is what the start
    // changed, [wanted] is what it meant to change, and [already] is what was already as the start wanted it.
    data class Started(
        val window: LocalDateTime,
        val changes: Changes,
        val wanted: Changes = changes,
        val already: Changes = Changes.NONE,
    )

    sealed interface Step {
        data class Start(val window: ScheduleResolver.Window) : Step

        // [schedule] is null when the end was missed. [until] is the window's own end when an edit stopped it early.
        data class End(val changes: Changes, val schedule: Schedule?, val until: LocalDateTime? = null) : Step

        data class Move(val started: Started) : Step

        data object None : Step
    }

    // A boundary between touching windows changes nothing.
    fun atAlarm(schedules: List<Schedule>, now: LocalDateTime, started: Started?): Step {
        val window = ScheduleResolver.window(schedules, now)
        if (window != null) {
            return if (window.inFirst && window.start != started?.window) Step.Start(window) else Step.None
        }
        if (started == null) return Step.None
        // A started window whose end was missed is forgotten, not undone.
        val ended = ScheduleResolver.lastEnded(schedules, now)
        if (ended == null || ended.window != started.window) return Step.End(Changes.NONE, null)
        return Step.End(ConnectivityPlan.end(started.changes, ended.schedule.enableWifi), ended.schedule)
    }

    // Returns the schedule of the running window when airplane mode is off and the app did not turn it on, for example
    // because Shizuku was not ready at the start. Returns null when airplane mode was already on at the start and the
    // user turned it off later. Also returns null while the start is still switching, and in the first minute of the
    // window, before the start alarm runs.
    fun missed(
        schedules: List<Schedule>,
        now: LocalDateTime,
        started: Started?,
        airplaneOn: Boolean,
        switching: Boolean = false,
    ): Schedule? {
        if (airplaneOn || switching) return null
        val window = ScheduleResolver.window(schedules, now) ?: return null
        if (started?.window != window.start && now < window.start.plusMinutes(1)) return null
        if (started?.window == window.start && (started.changes.airplane || started.already.airplane)) return null
        return ScheduleResolver.activeSchedule(schedules, now)
    }

    // Returns what a start saves before it switches, and what it switches. When the user turns on a missed start, the
    // saved [Started] keeps what the missed start changed and found, so the end still undoes and reports it.
    // [wifiKept] means that the user turned Wi-Fi on against an earlier start, so this start leaves Wi-Fi on.
    fun begin(
        window: ScheduleResolver.Window,
        started: Started?,
        airplaneOn: Boolean,
        wifiOn: Boolean,
        wifiKept: Boolean,
    ): Pair<Started, Changes> {
        val disableWifi = window.disableWifi && !wifiKept
        val wanted = ConnectivityPlan.start(airplaneOn, wifiOn, disableWifi)
        val record = started?.takeIf { it.window == window.start }?.let { it.copy(wanted = it.wanted + wanted) }
            ?: Started(window.start, Changes.NONE, wanted, ConnectivityPlan.already(airplaneOn, wifiOn, disableWifi))
        return record to wanted
    }

    // A schedule with [Schedule.turnOffIfAlreadyOn] turns off an airplane mode that was on at the start, although it
    // did not turn it on.
    fun takeOver(changes: Changes, schedule: Schedule, airplaneOn: Boolean): Changes =
        if (schedule.turnOffIfAlreadyOn && airplaneOn) changes.copy(airplane = true) else changes

    // What a forgotten window left switched. The app reports it and does not undo it.
    fun leftSwitched(started: Started, enableWifi: Boolean, airplaneOn: Boolean, wifiOn: Boolean): Changes {
        val undo = ConnectivityPlan.end(started.changes, enableWifi)
        return Changes(undo.airplane && airplaneOn, undo.wifi && !wifiOn)
    }

    fun atEdit(old: List<Schedule>, new: List<Schedule>, now: LocalDateTime, started: Started?): Step {
        val before = ScheduleResolver.window(old, now)
        val after = ScheduleResolver.window(new, now)
        if (before == null) return if (after != null) Step.Start(after) else Step.None
        val running = started?.takeIf { it.window == before.start }
        return when {
            after == null -> {
                val active = ScheduleResolver.activeSchedule(old, now)!!
                val schedule = new.find { it.id == active.id } ?: active
                Step.End(
                    running?.let {
                        ConnectivityPlan.end(it.changes, schedule.enableWifi)
                    } ?: Changes.NONE,
                    schedule,
                    ScheduleResolver.windowEnd(active, ScheduleResolver.nextDate(active, now)!!),
                )
            }
            running != null && after.start != before.start -> Step.Move(running.copy(window = after.start))
            else -> Step.None
        }
    }

    // If the user turned Wi-Fi on against the start option, Wi-Fi stays on when the window starts again before
    // [until]. [kept] is true when Wi-Fi already stayed on at the last start.
    fun keptWifi(started: Started?, wifiOn: Boolean, until: LocalDateTime?, kept: Boolean): LocalDateTime? =
        until?.takeIf { started != null && wifiOn && (kept || started.changes.wifi || started.already.wifi) }
}
