package io.github.probecat.airplanescheduler.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimeTest {
    @Test
    fun overnightBoundaries() {
        assertFalse(ScheduleTime.isActive(23 * 60, 7 * 60, 22 * 60 + 59))
        assertTrue(ScheduleTime.isActive(23 * 60, 7 * 60, 23 * 60))
        assertTrue(ScheduleTime.isActive(23 * 60, 7 * 60, 6 * 60 + 59))
        assertFalse(ScheduleTime.isActive(23 * 60, 7 * 60, 7 * 60))
    }

    @Test
    fun sameDayBoundaries() {
        assertFalse(ScheduleTime.isActive(9 * 60, 17 * 60, 8 * 60 + 59))
        assertTrue(ScheduleTime.isActive(9 * 60, 17 * 60, 9 * 60))
        assertTrue(ScheduleTime.isActive(9 * 60, 17 * 60, 16 * 60 + 59))
        assertFalse(ScheduleTime.isActive(9 * 60, 17 * 60, 17 * 60))
    }

    @Test
    fun equalTimesAreNeverActive() {
        assertFalse(ScheduleTime.isActive(0, 0, 0))
        assertFalse(ScheduleTime.isActive(12 * 60, 12 * 60, 23 * 60))
    }

    @Test
    fun durationCrossesMidnight() {
        assertEquals(8 * 60, ScheduleTime.duration(23 * 60, 7 * 60))
        assertEquals(8 * 60, ScheduleTime.duration(9 * 60, 17 * 60))
        assertEquals(0, ScheduleTime.duration(9 * 60, 9 * 60))
    }
}
