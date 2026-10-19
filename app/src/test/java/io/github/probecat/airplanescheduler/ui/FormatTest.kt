package io.github.probecat.airplanescheduler.ui

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val us = Format(is24Hour = false, Locale.US, TestStrings)
    private val uk = Format(is24Hour = true, Locale.UK, TestStrings)

    @Test
    fun aSingleRunOfThreeOrMoreDaysIsARange() {
        assertEquals("Mon–Fri", us.days(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)))
        assertEquals("Wed–Fri", us.days(setOf(WEDNESDAY, THURSDAY, FRIDAY)))
    }

    @Test
    fun otherDaysAreListedInWeekOrder() {
        assertEquals("Sun Mon Tue Thu Fri", us.days(setOf(FRIDAY, THURSDAY, TUESDAY, MONDAY, SUNDAY)))
        assertEquals("Sat Sun", uk.days(setOf(SATURDAY, SUNDAY)))
        assertEquals("Mon Tue", us.days(setOf(MONDAY, TUESDAY)))
    }

    @Test
    fun runsFollowTheLocalesFirstDayOfWeek() {
        assertEquals("Fri–Sun", uk.days(setOf(FRIDAY, SATURDAY, SUNDAY)))
        assertEquals("Sun Fri Sat", us.days(setOf(FRIDAY, SATURDAY, SUNDAY)))
    }

    @Test
    fun everyDayIsNamed() {
        assertEquals("Every day", us.days(Schedule.EVERY_DAY))
    }

    @Test
    fun onceShowsItsDayWhileOn() {
        val today = LocalDate.of(2026, 10, 9)
        val once = Schedule(id = 1, start = 0, end = 60, days = emptySet(), date = today)
        assertEquals("Today", us.repeats(once, today))
        assertEquals("Tomorrow", us.repeats(once.copy(date = today.plusDays(1)), today))
        assertEquals("Yesterday", us.repeats(once.copy(date = today.minusDays(1)), today))
        assertEquals("Not scheduled", us.repeats(once.copy(enabled = false), today))
        assertEquals("12:00\u00A0AM – 1:00\u00A0AM (today)", us.label(once, today))
    }

    @Test
    fun durationsDropMinutesFromADay() {
        val nbsp = '\u00A0'
        assertEquals("5${nbsp}min", us.duration(5))
        assertEquals("2${nbsp}h", us.duration(120))
        assertEquals("4${nbsp}h 2${nbsp}min", us.duration(242))
        assertEquals("1${nbsp}d", us.duration(24 * 60 + 30))
        assertEquals("2${nbsp}d 22${nbsp}h", us.duration(70 * 60 + 2))
    }
}
