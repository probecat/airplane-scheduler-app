package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// What the app does when a window starts or ends. It undoes only what it changed itself, and it
// skips a start or end that it missed instead of catching up later.
object Boundary {
    // The last window the app started, keyed by its chain's start: what it changed then, what it
    // meant to change, and what was already as the start wanted it.
    data class Started(
        val window: LocalDateTime,
        val changes: Changes,
        val wanted: Changes = changes,
        val already: Changes = Changes.NONE,
    )

    sealed interface Step {
        data class Start(val window: ScheduleResolver.Window) : Step

        // [schedule] is null when the end was missed.
        data class End(val changes: Changes, val schedule: Schedule?) : Step

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

    // The running window when airplane mode is off and the app didn't turn it on, such as when Shizuku
    // wasn't ready at the start. Not when it was already on at the start and someone turned it off since,
    // nor while the start is still switching or before its alarm had a minute to.
    fun missed(
        schedules: List<Schedule>,
        now: LocalDateTime,
        started: Started?,
        airplaneOn: Boolean,
        switching: Boolean = false,
    ): ScheduleResolver.Window? {
        if (airplaneOn || switching) return null
        val window = ScheduleResolver.window(schedules, now) ?: return null
        if (started?.window != window.start && now < window.start.plusMinutes(1)) return null
        return window.takeIf {
            started?.window != it.start || !(started.changes.airplane || started.already.airplane)
        }
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
                )
            }
            running != null && after.start != before.start -> Step.Move(running.copy(window = after.start))
            else -> Step.None
        }
    }
}
