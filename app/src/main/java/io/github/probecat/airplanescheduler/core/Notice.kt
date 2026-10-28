package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime

// [date] is the window's start date.
sealed interface Notice {
    val date: LocalDate

    // [after] is the schedule that is running until then, so airplane mode just stays on.
    data class Upcoming(
        override val date: LocalDate,
        val start: LocalDateTime,
        val postponed: Boolean,
        val after: Schedule? = null,
    ) : Notice

    // Postponed past its planned start, as when paused, so it can be resumed.
    data class Paused(override val date: LocalDate, val until: LocalDateTime) : Notice

    // [missed] when airplane mode didn't turn on at the start. [next] is the schedule that starts at its end,
    // so airplane mode stays on.
    data class Active(
        override val date: LocalDate,
        val end: LocalDateTime,
        val missed: Boolean,
        val next: Schedule? = null,
    ) : Notice

    // Skipped, or ended early once it was running, until the window would have ended.
    data class Skipped(override val date: LocalDate, val until: LocalDateTime) : Notice

    companion object {
        // [missed] says whether the running window's start was missed. [schedules] are all of them, for the
        // ones that touch it.
        fun of(schedule: Schedule, schedules: List<Schedule>, now: LocalDateTime, missed: Boolean): Notice? {
            if (!schedule.enabled) return null
            val ahead = now.plusMinutes(ScheduleResolver.AHEAD_MINUTES)
            ScheduleResolver.nextDate(schedule, now)?.let { date ->
                val start = ScheduleResolver.startOf(schedule, date)
                val delay = ScheduleResolver.delay(schedule, now)
                when {
                    !start.isAfter(now) -> return Active(
                        date,
                        ScheduleResolver.windowEnd(schedule, date),
                        missed,
                        ScheduleResolver.following(schedule, date, schedules),
                    )
                    delay?.due == true -> return Paused(date, start)
                    !start.isAfter(ahead) -> return Upcoming(
                        date,
                        start,
                        postponed = delay != null,
                        after = ScheduleResolver.preceding(schedule, date, schedules),
                    )
                }
            }
            val skipped = ScheduleResolver.pendingSkips(schedule, now)
                .filter { !ScheduleResolver.plannedStart(schedule, it).isAfter(ahead) }.minOrNull() ?: return null
            return Skipped(skipped, ScheduleResolver.windowEnd(schedule, skipped))
        }
    }
}
