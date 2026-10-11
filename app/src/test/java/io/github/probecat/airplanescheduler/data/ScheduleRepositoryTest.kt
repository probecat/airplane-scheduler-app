package io.github.probecat.airplanescheduler.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryTest {
    @Test
    fun daysAreStoredByName() {
        val json = ScheduleRepository.encode(
            listOf(Schedule(id = 1, start = 0, end = 60, days = setOf(DayOfWeek.MONDAY))),
        )
        assertTrue(json, json.contains("\"days\":[\"MONDAY\"]"))
    }

    @Test
    fun datesRoundTrip() {
        val once = listOf(Schedule(id = 1, start = 0, end = 60, days = emptySet(), date = LocalDate.of(2026, 10, 9)))
        val json = ScheduleRepository.encode(once)
        assertTrue(json, json.contains("\"date\":\"2026-10-09\""))
        assertEquals(once, ScheduleRepository.decode(json))
    }

    @Test
    fun missingFieldsUseDefaults() {
        assertEquals(
            listOf(Schedule(id = 3, start = 60, end = 120)),
            ScheduleRepository.decode("""[{"id":3,"start":60,"end":120,"future":1}]"""),
        )
    }

    @Test
    fun unreadableValueIsEmpty() {
        assertEquals(emptyList<Schedule>(), ScheduleRepository.decode(null))
        assertEquals(emptyList<Schedule>(), ScheduleRepository.decode("not json"))
    }
}
