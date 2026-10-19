package io.github.probecat.airplanescheduler.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTimeTest {
    @Test
    fun durationCrossesMidnight() {
        assertEquals(8 * 60, ScheduleTime.duration(23 * 60, 7 * 60))
        assertEquals(8 * 60, ScheduleTime.duration(9 * 60, 17 * 60))
        assertEquals(0, ScheduleTime.duration(9 * 60, 9 * 60))
    }
}
