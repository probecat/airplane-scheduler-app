package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// What the app does when a window starts or ends. It undoes only what it changed itself, and it
// skips a start or end that it missed instead of catching up later.
object Boundary {
    // The last window the app started, keyed by its chain's start: what it changed then, and what was
    // already as the start wanted it.
    data class Started(val window: LocalDateTime, val changes: Changes, val already: Changes = Changes.NONE)

    sealed interface Step {
        data class Start(val window: ScheduleResolver.Window) : Step

        // Forgets the started window and undoes [changes], if any.
        data class End(val changes: Changes) : Step

        // An edit moved the start of the window that is running.
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
        if (ended == null || ended.window != started.window) return Step.End(Changes.NONE)
        return Step.End(ConnectivityPlan.end(started.changes, ended.enableWifi))
    }

    // The running window when airplane mode is off and the app didn't turn it on, such as when Shizuku
    // wasn't ready at the start. Not when it was already on at the start and someone turned it off since.
    fun missed(
        schedules: List<Schedule>,
        now: LocalDateTime,
        started: Started?,
        airplaneOn: Boolean,
    ): ScheduleResolver.Window? {
        if (airplaneOn) return null
        val window = ScheduleResolver.window(schedules, now) ?: return null
        return window.takeIf {
            started?.window != it.start || !(started.changes.airplane || started.already.airplane)
        }
    }

    // A window that the edit switches on starts at once, and one that it switches off ends at once.
    fun atEdit(old: List<Schedule>, new: List<Schedule>, now: LocalDateTime, started: Started?): Step {
        val before = ScheduleResolver.window(old, now)
        val after = ScheduleResolver.window(new, now)
        if (before == null) return if (after != null) Step.Start(after) else Step.None
        // Null when the app missed this window's start, so it has nothing to undo or move.
        val running = started?.takeIf { it.window == before.start }
        return when {
            after == null -> {
                val active = ScheduleResolver.activeSchedule(old, now)!!
                val enableWifi = (new.find { it.id == active.id } ?: active).enableWifi
                Step.End(running?.let { ConnectivityPlan.end(it.changes, enableWifi) } ?: Changes.NONE)
            }
            running != null && after.start != before.start -> Step.Move(running.copy(window = after.start))
            else -> Step.None
        }
    }
}
