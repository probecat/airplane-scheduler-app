package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// When windows run, which ones may coexist, and when the app's alarms come. All in wall-clock time.
class TimingTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun `an overnight window belongs to the day it starts`() {
        // A Friday-only night runs from Friday 23:00 into Saturday, and early Friday isn't part of it.
        val fridays = night.copy(days = setOf(FRIDAY))
        assertTrue(ScheduleResolver.isActive(fridays, friday(23)))
        assertTrue(ScheduleResolver.isActive(fridays, saturday(6, 59)))
        assertFalse(ScheduleResolver.isActive(fridays, saturday(7)))
        assertFalse(ScheduleResolver.isActive(fridays, friday(3)))
    }

    @Test
    fun `a window runs only on its days, and Sunday night runs into Monday`() {
        val weekend = Schedule(id = 2, start = at(13), end = at(15), days = setOf(SATURDAY, SUNDAY))
        assertFalse(ScheduleResolver.isActive(weekend, friday(14)))
        assertTrue(ScheduleResolver.isActive(weekend, saturday(14)))
        val sundays = night.copy(days = setOf(SUNDAY))
        assertTrue(ScheduleResolver.isActive(sundays, LocalDateTime.of(2026, 10, 12, 6, 30)))
    }

    @Test
    fun `a schedule that runs once takes the first window that hasn't ended`() {
        // Saved at noon it's tonight's; saved at 03:00 it's the one already running since last night.
        val once = night.copy(days = emptySet())
        assertEquals(fridayDate, ScheduleResolver.dated(once, friday(12)).date)
        assertEquals(fridayDate.minusDays(1), ScheduleResolver.dated(once, friday(3)).date)
    }

    @Test
    fun `a schedule that runs once runs on its date only, then switches itself off`() {
        val phone = Phone(night.copy(days = emptySet(), date = fridayDate), now = friday(22))
        phone.passTo(friday(23))
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertFalse(phone.schedule().enabled)
        phone.passTo(saturday(23, 30))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `overlapping schedules are refused, touching ones aren't`() {
        // Overlaps are checked across midnight and the week's end, and only on the days both run.
        val all = listOf(night)
        assertEquals(night, ScheduleEdits.conflict(all, Schedule(id = 2, start = at(21), end = at(23, 30)), friday(12)))
        assertEquals(night, ScheduleEdits.conflict(all, Schedule(id = 2, start = at(6), end = at(8)), friday(12)))
        assertNull(ScheduleEdits.conflict(all, Schedule(id = 2, start = at(7), end = at(9)), friday(12)))
        assertNull(ScheduleEdits.conflict(all, Schedule(id = 2, start = at(20), end = at(23)), friday(12)))
        val sundays = listOf(night.copy(days = setOf(SUNDAY)))
        val monday = Schedule(id = 2, start = at(6), end = at(8), days = setOf(MONDAY))
        assertEquals(sundays.single(), ScheduleEdits.conflict(sundays, monday, friday(12)))
        assertNull(ScheduleEdits.conflict(sundays, monday.copy(days = setOf(SUNDAY)), friday(12)))
    }

    @Test
    fun `a schedule that runs once overlaps only around its date`() {
        val saturdayMorning =
            Schedule(id = 2, start = at(6), end = at(8), days = emptySet(), date = fridayDate.plusDays(1))
        assertEquals(night, ScheduleEdits.conflict(listOf(night), saturdayMorning, friday(12)))
        assertNull(ScheduleEdits.conflict(listOf(night.copy(days = setOf(SATURDAY))), saturdayMorning, friday(12)))
    }

    @Test
    fun `switched-off schedules and ones that ran once and are over may overlap anything`() {
        val other = Schedule(id = 2, start = at(22), end = at(23, 30))
        assertNull(ScheduleEdits.conflict(listOf(night.copy(enabled = false)), other, friday(12)))
        assertNull(ScheduleEdits.conflict(listOf(night), other.copy(enabled = false), friday(12)))
        val over = night.copy(days = emptySet(), date = fridayDate.minusDays(2))
        assertNull(ScheduleEdits.conflict(listOf(over), other, friday(12)))
    }

    @Test
    fun `a window with the same start and end never runs`() {
        val phone = Phone(night.copy(end = night.start), now = friday(22))
        phone.passTo(saturday(8))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `touching counts only on the days both windows run`() {
        // Friday-only night then a daily morning: on Friday morning the morning starts on its own, and its
        // own Wi-Fi option applies.
        val fridayNight = night.copy(days = setOf(FRIDAY))
        val morning = Schedule(id = 2, start = at(7), end = at(9), disableWifi = false)
        val phone = Phone(fridayNight, morning, now = friday(6))
        phone.passTo(friday(7))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        phone.passTo(friday(23))
        assertFalse(phone.wifiOn)
        phone.passTo(saturday(8))
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `windows covering the whole day don't loop forever looking for the chain's start`() {
        val day = Schedule(id = 1, start = at(7), end = at(19))
        val nightShift = Schedule(id = 2, start = at(19), end = at(7))
        assertTrue(ScheduleResolver.window(listOf(day, nightShift), friday(12)) != null)
    }

    @Test
    fun `alarms come at each start and end, and the notice an hour before a start`() {
        val lunch = Schedule(id = 2, start = at(12), end = at(13))
        val schedules = listOf(night, lunch)
        assertEquals(friday(7), ScheduleResolver.nextChange(schedules, friday(5).atZone(utc))?.toLocalDateTime())
        assertEquals(friday(13), ScheduleResolver.nextChange(schedules, friday(12, 30).atZone(utc))?.toLocalDateTime())
        assertEquals(friday(22), ScheduleResolver.nextNotice(schedules, friday(13).atZone(utc), 60)?.toLocalDateTime())
    }

    @Test
    fun `a skipped window sets no start alarm, and a postponed one starts late but ends on time`() {
        val skipped = ScheduleResolver.skip(night, friday(12))
        assertEquals(saturday(23), ScheduleResolver.nextStart(skipped, friday(12).atZone(utc))?.toLocalDateTime())
        val postponed = ScheduleResolver.postpone(night, friday(22, 30), 30)
        assertEquals(
            friday(23, 30),
            ScheduleResolver.nextStart(postponed, friday(22, 30).atZone(utc))?.toLocalDateTime(),
        )
        assertEquals(saturday(7), ScheduleResolver.nextEnd(postponed, friday(22, 30).atZone(utc))?.toLocalDateTime())
    }

    @Test
    fun `a start in a daylight saving gap comes when the clock jumps past it`() {
        // 02:30 doesn't exist on 8 March 2026 in New York, so the window starts at 03:30 instead of never.
        val zone = ZoneId.of("America/New_York")
        val schedule = Schedule(id = 2, start = at(2, 30), end = at(5))
        val start = ScheduleResolver.nextStart(schedule, ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, zone))!!
        assertEquals(LocalDateTime.of(2026, 3, 8, 3, 30), start.toLocalDateTime())
    }
}
