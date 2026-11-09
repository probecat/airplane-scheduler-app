package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Notice {
    val date: LocalDate

    data class Upcoming(override val date: LocalDate, val start: LocalDateTime, val postponed: Boolean) : Notice

    data class Paused(override val date: LocalDate, val until: LocalDateTime) : Notice

    data class Active(override val date: LocalDate, val end: LocalDateTime, val missed: Boolean) : Notice

    data class Skipped(override val date: LocalDate, val until: LocalDateTime) : Notice

    companion object {
        fun of(schedule: Schedule, now: LocalDateTime, missed: Boolean): Notice? =
            of(ScheduleStatus.of(schedule, now, missed))

        fun of(status: ScheduleStatus?): Notice? = when (status) {
            is ScheduleStatus.Running -> Active(status.date, status.end, status.missed)
            is ScheduleStatus.Paused -> Paused(status.date, status.until)
            is ScheduleStatus.Upcoming -> Upcoming(status.date, status.start, status.postponed)
                .takeIf { status.soon }
            is ScheduleStatus.Skipped -> Skipped(status.date, status.until).takeIf { status.soon }
            null -> null
        }
    }
}
