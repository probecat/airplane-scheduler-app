package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime

// A window the user skipped or postponed shows no notice until it runs. Its undo is in the app.
sealed interface Notice {
    val date: LocalDate

    data class Upcoming(override val date: LocalDate, val start: LocalDateTime) : Notice

    data class Paused(override val date: LocalDate, val until: LocalDateTime) : Notice

    data class Active(override val date: LocalDate, val end: LocalDateTime, val missed: Boolean) : Notice

    companion object {
        // [lead] is how many minutes before its start an upcoming window shows. 0 shows none.
        fun of(schedule: Schedule, now: LocalDateTime, missed: Boolean, lead: Long): Notice? =
            of(ScheduleStatus.of(schedule, now, missed), now, lead)

        fun of(status: ScheduleStatus?, now: LocalDateTime, lead: Long): Notice? = when (status) {
            is ScheduleStatus.Running -> Active(status.date, status.end, status.missed)
            is ScheduleStatus.Paused -> Paused(status.date, status.until).takeIf { !status.postponed }
            is ScheduleStatus.Upcoming -> Upcoming(status.date, status.start)
                .takeIf { lead > 0 && !status.postponed && !status.start.isAfter(now.plusMinutes(lead)) }
            is ScheduleStatus.Skipped, null -> null
        }
    }
}
