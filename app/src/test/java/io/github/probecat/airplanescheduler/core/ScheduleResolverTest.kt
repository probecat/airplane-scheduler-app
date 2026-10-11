package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ScheduleResolver.Ended
import io.github.probecat.airplanescheduler.core.ScheduleResolver.Window
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleResolverTest {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    // 2026-10-09 is a Friday.
    private fun friday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 9, hour, minute)

    private val night = Schedule(id = 1, name = "Night", start = at(23), end = at(7))

    private val thursdayNight = friday(23).minusDays(1)

    @Test
    fun emptyListHasNoWindowAndNoConflict() {
        assertNull(ScheduleResolver.window(emptyList(), friday(12)))
        assertNull(ScheduleResolver.activeSchedule(emptyList(), friday(12)))
        assertNull(ScheduleResolver.conflict(night, emptyList()))
        assertNull(ScheduleResolver.nextStart(emptyList(), ZonedDateTime.now()))
    }

    @Test
    fun disabledSchedulesAreIgnored() {
        val off = night.copy(enabled = false)
        assertNull(ScheduleResolver.window(listOf(off), friday(23, 30)))
        assertNull(ScheduleResolver.conflict(night.copy(id = 2), listOf(off)))
    }

    @Test
    fun overnightWindowBelongsToTheDayItStarts() {
        val fridays = night.copy(days = setOf(FRIDAY))
        assertTrue(ScheduleResolver.isActive(fridays, friday(23)))
        assertTrue(ScheduleResolver.isActive(fridays, friday(23).plusHours(7).minusMinutes(1)))
        assertFalse(ScheduleResolver.isActive(fridays, friday(23).plusHours(8)))
        // Friday 03:00 belongs to Thursday's window, which doesn't exist.
        assertFalse(ScheduleResolver.isActive(fridays, friday(3)))
    }

    @Test
    fun sameDayWindowFollowsDays() {
        val weekend = Schedule(id = 1, start = at(13), end = at(15), days = setOf(SATURDAY, SUNDAY))
        assertFalse(ScheduleResolver.isActive(weekend, friday(14)))
        assertTrue(ScheduleResolver.isActive(weekend, friday(14).plusDays(1)))
        assertTrue(ScheduleResolver.isActive(weekend, friday(14).plusDays(2)))
        assertFalse(ScheduleResolver.isActive(weekend, friday(15).plusDays(2)))
    }

    @Test
    fun windowKeepsItsStartAndWifiOption() {
        val schedules = listOf(night.copy(disableWifi = false))
        val window = Window(friday(23), inFirst = true, disableWifi = false)
        assertEquals(window, ScheduleResolver.window(schedules, friday(23, 30)))
        assertEquals(window, ScheduleResolver.window(schedules, friday(3).plusDays(1)))
        assertNull(ScheduleResolver.window(schedules, friday(12)))
    }

    @Test
    fun overlappingWindowsConflict() {
        val evening = Schedule(id = 2, start = at(21), end = at(23, 30))
        assertEquals(night, ScheduleResolver.conflict(evening, listOf(night)))
        val early = Schedule(id = 3, start = at(6), end = at(8))
        assertEquals(night, ScheduleResolver.conflict(early, listOf(night)))
    }

    @Test
    fun scheduleNeverConflictsWithItself() {
        assertNull(ScheduleResolver.conflict(night.copy(start = at(22)), listOf(night)))
    }

    @Test
    fun overnightConflictCrossesIntoTheNextDay() {
        val fridayNight = night.copy(days = setOf(FRIDAY))
        val saturdayMorning = Schedule(id = 2, start = at(5), end = at(9), days = setOf(SATURDAY))
        val fridayMorning = Schedule(id = 3, start = at(5), end = at(9), days = setOf(FRIDAY))
        assertEquals(fridayNight, ScheduleResolver.conflict(saturdayMorning, listOf(fridayNight)))
        assertNull(ScheduleResolver.conflict(fridayMorning, listOf(fridayNight)))
    }

    @Test
    fun sundayNightWrapsIntoMonday() {
        val sundayNight = night.copy(days = setOf(SUNDAY))
        val mondayMorning = Schedule(id = 2, start = at(6), end = at(8), days = setOf(MONDAY))
        assertEquals(sundayNight, ScheduleResolver.conflict(mondayMorning, listOf(sundayNight)))
        assertTrue(ScheduleResolver.isActive(sundayNight, LocalDateTime.of(2026, 10, 12, 6, 30)))
    }

    @Test
    fun differentDaysDoNotConflict() {
        val weekdays = Schedule(id = 1, start = at(9), end = at(17), days = setOf(MONDAY, FRIDAY))
        val weekend = Schedule(id = 2, start = at(9), end = at(17), days = setOf(SATURDAY, SUNDAY))
        assertNull(ScheduleResolver.conflict(weekend, listOf(weekdays)))
    }

    @Test
    fun touchingWindowsDoNotConflict() {
        val morning = Schedule(id = 2, start = at(7), end = at(9))
        val evening = Schedule(id = 3, start = at(20), end = at(23))
        assertNull(ScheduleResolver.conflict(morning, listOf(night)))
        assertNull(ScheduleResolver.conflict(evening, listOf(night)))
    }

    @Test
    fun touchingWindowsMergeAndWifiFollowsTheOuterEdges() {
        val first = night.copy(disableWifi = true, enableWifi = false)
        val second = Schedule(id = 2, start = at(7), end = at(9), disableWifi = false, enableWifi = true)
        val schedules = listOf(second, first)
        // The chain starts on Thursday night, and Wi-Fi keeps the first window's option through 07:00.
        assertEquals(
            Window(thursdayNight, inFirst = true, disableWifi = true),
            ScheduleResolver.window(schedules, friday(6, 59)),
        )
        assertEquals(
            Window(thursdayNight, inFirst = false, disableWifi = true),
            ScheduleResolver.window(schedules, friday(7)),
        )
        assertEquals(
            Window(thursdayNight, inFirst = false, disableWifi = true),
            ScheduleResolver.window(schedules, friday(8, 59)),
        )
        // At the far end, Wi-Fi follows the last window.
        assertNull(ScheduleResolver.window(schedules, friday(9)))
        assertEquals(Ended(thursdayNight, enableWifi = true), ScheduleResolver.lastEnded(schedules, friday(9)))
        assertEquals(Ended(thursdayNight, enableWifi = true), ScheduleResolver.lastEnded(schedules, friday(22)))
    }

    @Test
    fun touchingOnlyCountsOnTheDaysBothWindowsRun() {
        val fridayNight = night.copy(days = setOf(FRIDAY), disableWifi = true)
        val morning = Schedule(id = 2, start = at(7), end = at(9), disableWifi = false)
        // Friday 07:00 follows Thursday night, which isn't scheduled.
        assertEquals(
            Window(friday(7), inFirst = true, disableWifi = false),
            ScheduleResolver.window(listOf(fridayNight, morning), friday(8)),
        )
        // Saturday 07:00 follows Friday night.
        assertEquals(
            Window(friday(23), inFirst = false, disableWifi = true),
            ScheduleResolver.window(listOf(fridayNight, morning), friday(8).plusDays(1)),
        )
    }

    @Test
    fun windowsCoveringTheWholeDayDoNotLoop() {
        val day = Schedule(id = 1, start = at(7), end = at(19), disableWifi = false)
        val night = Schedule(id = 2, start = at(19), end = at(7), disableWifi = true)
        assertNotNull(ScheduleResolver.window(listOf(day, night), friday(12)))
    }

    @Test
    fun lastEndedIsTheMostRecentEnd() {
        val lunch = Schedule(id = 2, start = at(12), end = at(13), enableWifi = false)
        val schedules = listOf(night.copy(enableWifi = true), lunch)
        assertEquals(Ended(thursdayNight, enableWifi = true), ScheduleResolver.lastEnded(schedules, friday(10)))
        assertEquals(Ended(friday(12), enableWifi = false), ScheduleResolver.lastEnded(schedules, friday(14)))
        assertNull(ScheduleResolver.lastEnded(emptyList(), friday(14)))
    }

    @Test
    fun nextStartSkipsDaysOff() {
        val zone = ZoneId.of("Asia/Dhaka")
        val weekend = night.copy(days = setOf(SATURDAY))
        val now = friday(23, 30).atZone(zone)
        assertEquals(LocalDateTime.of(2026, 10, 10, 23, 0).atZone(zone), ScheduleResolver.nextStart(weekend, now))
    }

    @Test
    fun nextEndFinishesTheCurrentWindow() {
        val zone = ZoneId.of("Asia/Dhaka")
        val fridays = night.copy(days = setOf(FRIDAY))
        val now = friday(23, 30).atZone(zone)
        assertEquals(LocalDateTime.of(2026, 10, 10, 7, 0).atZone(zone), ScheduleResolver.nextEnd(fridays, now))
        // After that window, the next end is a week later.
        assertEquals(
            LocalDateTime.of(2026, 10, 17, 7, 0).atZone(zone),
            ScheduleResolver.nextEnd(fridays, now.plusHours(8)),
        )
    }

    @Test
    fun nextStartIsTheEarliestAcrossSchedules() {
        val zone = ZoneId.of("Asia/Dhaka")
        val lunch = Schedule(id = 2, start = at(12), end = at(13))
        val now = friday(10).atZone(zone)
        assertEquals(friday(12).atZone(zone), ScheduleResolver.nextStart(listOf(night, lunch), now))
        assertEquals(
            friday(23).atZone(zone),
            ScheduleResolver.nextStart(listOf(night, lunch.copy(enabled = false)), now),
        )
    }

    @Test
    fun nextChangeIsTheEarliestStartOrEndAfterNow() {
        val zone = ZoneId.of("Asia/Dhaka")
        val lunch = Schedule(id = 2, start = at(12), end = at(13))
        val schedules = listOf(night, lunch)
        assertEquals(friday(7).atZone(zone), ScheduleResolver.nextChange(schedules, friday(5).atZone(zone)))
        assertEquals(friday(13).atZone(zone), ScheduleResolver.nextChange(schedules, friday(12, 30).atZone(zone)))
        assertEquals(friday(23).atZone(zone), ScheduleResolver.nextChange(schedules, friday(13).atZone(zone)))
        assertNull(ScheduleResolver.nextChange(schedules.map { it.copy(enabled = false) }, friday(13).atZone(zone)))
    }

    @Test
    fun reminderComesAnHourBeforeTheNextStart() {
        val zone = ZoneId.of("Asia/Dhaka")
        assertEquals(friday(22).atZone(zone), ScheduleResolver.nextReminder(listOf(night), friday(12).atZone(zone), 60))
        // Within the last hour, the reminder moves to tomorrow's start.
        assertEquals(
            friday(22).plusDays(1).atZone(zone),
            ScheduleResolver.nextReminder(listOf(night), friday(22, 30).atZone(zone), 60),
        )
    }

    @Test
    fun daylightSavingGapMovesBoundaryForward() {
        val zone = ZoneId.of("America/New_York")
        val schedule = Schedule(id = 1, start = at(2, 30), end = at(5))
        val now = ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, zone)
        val start = ScheduleResolver.nextStart(schedule, now)!!
        assertEquals(3, start.hour)
        assertEquals(30, start.minute)
        assertEquals(8, start.dayOfMonth)
    }

    @Test
    fun daylightSavingOverlapKeepsWallClockTimes() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 10, 31, 23, 30, 0, 0, zone)
        val end = ScheduleResolver.nextEnd(night, now)!!
        assertEquals(LocalDateTime.of(2026, 11, 1, 7, 0), end.toLocalDateTime())
    }

    @Test
    fun onceRunsOnlyOnItsDate() {
        val once = night.copy(days = emptySet(), date = friday(0).toLocalDate())
        assertTrue(ScheduleResolver.isActive(once, friday(23)))
        assertTrue(ScheduleResolver.isActive(once, friday(6).plusDays(1)))
        assertFalse(ScheduleResolver.isActive(once, friday(23).plusDays(7)))
        assertFalse(ScheduleResolver.isActive(once, thursdayNight))
        val zone = ZoneId.of("UTC")
        assertEquals(friday(23), ScheduleResolver.nextStart(once, friday(12).atZone(zone))!!.toLocalDateTime())
        assertEquals(friday(7).plusDays(1), ScheduleResolver.nextEnd(once, friday(12).atZone(zone))!!.toLocalDateTime())
        assertNull(ScheduleResolver.nextStart(once, friday(23, 30).atZone(zone)))
        assertNull(ScheduleResolver.nextChange(listOf(once), friday(8).plusDays(1).atZone(zone)))
        assertEquals(Ended(friday(23), true), ScheduleResolver.lastEnded(listOf(once), friday(8).plusDays(1)))
    }

    @Test
    fun onceTakesTheFirstWindowThatHasNotEnded() {
        val once = night.copy(days = emptySet())
        val today = friday(0).toLocalDate()
        assertEquals(today, ScheduleResolver.dated(once, friday(12)).date)
        assertEquals(today, ScheduleResolver.dated(once, friday(23, 30)).date)
        // Thursday's window is still running.
        assertEquals(today.minusDays(1), ScheduleResolver.dated(once, friday(3)).date)
        val morning = once.copy(start = at(6), end = at(8))
        assertEquals(today.plusDays(1), ScheduleResolver.dated(morning, friday(9)).date)
        // A date whose window hasn't ended stays; one that's over moves on.
        val tomorrow = once.copy(date = today.plusDays(1))
        assertEquals(tomorrow, ScheduleResolver.dated(tomorrow, friday(12)))
        assertEquals(
            today.plusDays(1),
            ScheduleResolver.dated(once.copy(date = today.minusDays(3)), friday(23, 30).plusDays(1)).date,
        )
        assertNull(ScheduleResolver.dated(night.copy(date = today), friday(12)).date)
    }

    @Test
    fun onceSwitchesOffAfterItEnds() {
        val once = night.copy(days = emptySet(), date = friday(0).toLocalDate())
        assertFalse(ScheduleResolver.hasEnded(once, friday(6).plusDays(1)))
        assertTrue(ScheduleResolver.hasEnded(once, friday(7).plusDays(1)))
        assertEquals(listOf(once), ScheduleResolver.expire(listOf(once), friday(6).plusDays(1)))
        assertEquals(
            listOf(once.copy(enabled = false), night),
            ScheduleResolver.expire(listOf(once, night), friday(7).plusDays(1)),
        )
    }

    @Test
    fun onceConflictsOnlyAroundItsDate() {
        val friday = friday(0).toLocalDate()
        val once = Schedule(id = 2, start = at(6), end = at(8), days = emptySet(), date = friday.plusDays(1))
        // Friday night's window runs into Saturday morning.
        assertEquals(night, ScheduleResolver.conflict(once, listOf(night)))
        assertNull(ScheduleResolver.conflict(once, listOf(night.copy(days = setOf(SATURDAY)))))
        val otherOnce = once.copy(id = 3, start = at(7), end = at(9))
        assertEquals(once, ScheduleResolver.conflict(otherOnce, listOf(once)))
        assertNull(ScheduleResolver.conflict(otherOnce.copy(date = friday), listOf(once)))
        assertNull(ScheduleResolver.conflict(otherOnce.copy(start = at(8), end = at(9)), listOf(once)))
        val fridayNight = night.copy(id = 4, days = emptySet(), date = friday)
        assertEquals(fridayNight, ScheduleResolver.conflict(once, listOf(fridayNight)))
    }

    @Test
    fun invalidSchedulesNeverRun() {
        val empty = night.copy(days = emptySet())
        val zero = night.copy(end = night.start)
        assertNull(ScheduleResolver.window(listOf(empty, zero), friday(23, 30)))
        assertNull(ScheduleResolver.conflict(zero, listOf(night)))
    }
}
