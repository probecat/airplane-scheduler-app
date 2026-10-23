package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoticeTest {
    private fun friday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 9, hour, minute)

    private val today = friday(0).toLocalDate()
    private val night = Schedule(id = 1, start = 23 * 60, end = 7 * 60)

    @Test
    fun upcomingOnlyWithinTheHourBeforeTheStart() {
        assertNull(Notice.of(night, listOf(night), friday(21, 59), missed = false))
        assertEquals(
            Notice.Upcoming(today, friday(23), postponed = false),
            Notice.of(night, listOf(night), friday(22), false),
        )
        assertNull(Notice.of(night.copy(enabled = false), listOf(night.copy(enabled = false)), friday(22, 30), false))
    }

    @Test
    fun postponedIsUpcomingUntilItsPlannedStart() {
        val postponed = ScheduleResolver.postpone(night, friday(22, 50), 60)
        val start = friday(23).plusHours(1)
        assertEquals(
            Notice.Upcoming(today, start, postponed = true),
            Notice.of(postponed, listOf(postponed), friday(22, 55), false),
        )
        assertEquals(Notice.Paused(today, start), Notice.of(postponed, listOf(postponed), friday(23, 10), false))
    }

    @Test
    fun activeUntilTheEnd() {
        val end = friday(7).plusDays(1)
        assertEquals(Notice.Active(today, end, missed = false), Notice.of(night, listOf(night), friday(23), false))
        assertEquals(Notice.Active(today, end, missed = true), Notice.of(night, listOf(night), friday(23, 30), true))
        assertNull(Notice.of(night, listOf(night), end, false))
    }

    @Test
    fun pausedUntilItResumes() {
        val paused = ScheduleResolver.postpone(night, friday(23, 30), 15)
        assertEquals(Notice.Paused(today, friday(23, 45)), Notice.of(paused, listOf(paused), friday(23, 35), false))
    }

    @Test
    fun skippedUntilTheWindowWouldHaveEnded() {
        val end = friday(7).plusDays(1)
        val skipped = ScheduleResolver.skip(night, friday(22, 30))
        assertEquals(
            Notice.Skipped(today, until = end),
            Notice.of(skipped, listOf(skipped), friday(22, 30), false),
        )
        val ended = ScheduleResolver.skip(night, friday(23, 30))
        assertEquals(
            Notice.Skipped(today, until = end),
            Notice.of(ended, listOf(ended), friday(23, 30), false),
        )
        assertNull(Notice.of(ended, listOf(ended), end, false))
        // A skip far off shows only once its start comes near.
        assertNull(Notice.of(skipped, listOf(skipped), friday(12), false))
    }

    @Test
    fun touchingWindowsReadAsOne() {
        val morning = Schedule(id = 2, start = 7 * 60, end = 8 * 60)
        val both = listOf(night, morning)
        val saturday = today.plusDays(1)
        val seven = friday(7).plusDays(1)
        assertEquals(Notice.Active(today, seven, false, next = morning), Notice.of(night, both, friday(23), false))
        assertEquals(
            Notice.Upcoming(saturday, seven, postponed = false, after = night),
            Notice.of(morning, both, friday(6, 30).plusDays(1), false),
        )
        // Leaving out the second window ends the first one at its end.
        val skipped = ScheduleResolver.skip(morning, friday(6, 30).plusDays(1))
        assertEquals(Notice.Active(today, seven, false), Notice.of(night, listOf(night, skipped), friday(23), false))
        val postponed = ScheduleResolver.postpone(morning, friday(6, 30).plusDays(1), 15)
        assertEquals(Notice.Active(today, seven, false), Notice.of(night, listOf(night, postponed), friday(23), false))
    }
}
