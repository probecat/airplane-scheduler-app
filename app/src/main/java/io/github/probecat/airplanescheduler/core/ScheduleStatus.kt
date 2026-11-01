package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime

// A tap offered for one status must not act on the next status. [date] is the start date of the window.
sealed interface ScheduleStatus {
    val date: LocalDate

    // [after] is the schedule that runs until the start, so airplane mode stays on.
    data class Upcoming(
        override val date: LocalDate,
        val start: LocalDateTime,
        val postponed: Boolean,
        val soon: Boolean,
        val after: Schedule? = null,
    ) : ScheduleStatus

    data class Paused(override val date: LocalDate, val until: LocalDateTime) : ScheduleStatus

    // [next] starts at the end of this window, so airplane mode stays on.
    data class Running(
        override val date: LocalDate,
        val end: LocalDateTime,
        val missed: Boolean,
        val next: Schedule? = null,
    ) : ScheduleStatus

    // [underway] is true after the planned start passed.
    data class Skipped(
        override val date: LocalDate,
        val until: LocalDateTime,
        val endedEarly: Boolean,
        val underway: Boolean,
        val soon: Boolean,
    ) : ScheduleStatus

    enum class Action { SKIP, POSTPONE, UNDO, TURN_ON }

    val actions: Set<Action>
        get() = when (this) {
            is Upcoming -> setOfNotNull(
                Action.SKIP.takeIf { soon },
                Action.POSTPONE.takeIf { soon && !postponed },
                Action.UNDO.takeIf { postponed },
            )
            is Paused -> setOf(Action.SKIP, Action.UNDO)
            is Running -> if (missed) setOf(Action.SKIP, Action.TURN_ON) else setOf(Action.SKIP, Action.POSTPONE)
            is Skipped -> setOf(Action.UNDO)
        }

    fun allows(action: Action, date: LocalDate): Boolean = date == this.date && action in actions

    companion object {
        fun of(schedule: Schedule, schedules: List<Schedule>, now: LocalDateTime, missed: Boolean): ScheduleStatus? {
            if (!schedule.enabled) return null
            val ahead = now.plusMinutes(ScheduleResolver.AHEAD_MINUTES)
            val upcoming = ScheduleResolver.nextDate(schedule, now)?.let { date ->
                val start = ScheduleResolver.startOf(schedule, date)
                if (!start.isAfter(now)) {
                    val next = ScheduleResolver.following(schedule, date, schedules)
                    return Running(date, ScheduleResolver.windowEnd(schedule, date), missed, next)
                }
                val delay = ScheduleResolver.delay(schedule, now)
                if (delay?.due == true) return Paused(date, start)
                val after = ScheduleResolver.preceding(schedule, date, schedules)
                Upcoming(date, start, postponed = delay != null, soon = !start.isAfter(ahead), after = after)
            }
            if (upcoming?.soon == true) return upcoming
            val skips = ScheduleResolver.pendingSkips(schedule, now)
            val skipped = skips.minOrNull() ?: return upcoming
            return Skipped(
                skipped,
                ScheduleResolver.windowEnd(schedule, skipped),
                endedEarly = skips.any { it in schedule.endedEarly },
                underway = skips.any { !ScheduleResolver.plannedStart(schedule, it).isAfter(now) },
                soon = !ScheduleResolver.plannedStart(schedule, skipped).isAfter(ahead),
            )
        }
    }
}
