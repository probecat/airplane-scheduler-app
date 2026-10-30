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

    @Test
    fun freeSlotStartsAtNextFullHour() {
        assertEquals(15 * 60, ScheduleTime.freeSlot(14 * 60 + 20, 60, 30) { _, _ -> true })
        assertEquals(15 * 60, ScheduleTime.freeSlot(14 * 60, 60, 30) { _, _ -> true })
        assertEquals(0, ScheduleTime.freeSlot(23 * 60 + 30, 60, 30) { _, _ -> true })
    }

    @Test
    fun freeSlotStepsPastTakenWindows() {
        val taken = 15 * 60 until 17 * 60
        val slot = ScheduleTime.freeSlot(14 * 60 + 20, 60, 30) { start, end -> start !in taken && end - 1 !in taken }
        assertEquals(17 * 60, slot)
    }

    @Test
    fun freeSlotWrapsPastMidnight() {
        var ends = listOf<Int>()
        val slot = ScheduleTime.freeSlot(22 * 60 + 10, 60, 30) { start, end ->
            ends = ends + end
            start < 12 * 60
        }
        assertEquals(0, slot)
        assertEquals(listOf(0, 30, 60), ends)
    }

    @Test
    fun freeSlotFallsBackToFirstWhenFull() {
        assertEquals(9 * 60, ScheduleTime.freeSlot(8 * 60 + 5, 60, 30) { _, _ -> false })
    }

    @Test
    fun movedStartKeepsLength() {
        assertEquals(15 * 60, ScheduleTime.movedEnd(23 * 60, 7 * 60, 7 * 60))
        assertEquals(0, ScheduleTime.movedEnd(22 * 60, 23 * 60, 23 * 60))
        assertEquals(9 * 60, ScheduleTime.movedEnd(10 * 60, 10 * 60, 8 * 60))
    }

    @Test
    fun endPickedAtStartMovesAnHourLater() {
        assertEquals(8 * 60, ScheduleTime.pickedEnd(7 * 60, 7 * 60))
        assertEquals(0, ScheduleTime.pickedEnd(23 * 60, 23 * 60))
        assertEquals(6 * 60, ScheduleTime.pickedEnd(7 * 60, 6 * 60))
    }
}
