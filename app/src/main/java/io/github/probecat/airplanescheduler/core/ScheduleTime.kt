package io.github.probecat.airplanescheduler.core

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

object ScheduleTime {
    const val DAY = 24 * 60

    // A time in a DST gap shifts later by the length of the gap.
    fun at(time: LocalDateTime, zone: ZoneId): ZonedDateTime = ZonedDateTime.of(time, zone)

    fun duration(start: Int, end: Int): Int = Math.floorMod(end - start, DAY)

    fun format(minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
}
