package io.github.probecat.airplanescheduler.data

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine
import java.time.DayOfWeek.MONDAY
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

// What the app saved must stay readable after an update. Lost schedules, or a lost record of what a running window
// switched, are worse than any crash.
class StorageTest {
    private val window = LocalDateTime.of(2026, 10, 9, 23, 0)
    private val both = Changes(airplane = true, wifi = true)
    private val airplane = Changes(airplane = true, wifi = false)

    @Test
    fun `saved schedules still load`() {
        // Written in the format that the app stores. A change to the name or format of a field breaks this test, and
        // existing users.
        val stored = """[{"id":1,"name":"Night","start":1380,"end":420,"days":["MONDAY"],"date":"2026-10-09",""" +
            """"enabled":false,"disableWifi":false,"enableWifi":false,"skipped":["2026-10-09"],""" +
            """"endedEarly":["2026-10-09"],"delayed":{"2026-10-10":30}}]"""
        val date = LocalDate.of(2026, 10, 9)
        val schedule = Schedule(
            id = 1,
            name = "Night",
            start = 1380,
            end = 420,
            days = setOf(MONDAY),
            date = date,
            enabled = false,
            disableWifi = false,
            enableWifi = false,
            skipped = setOf(date),
            endedEarly = setOf(date),
            delayed = mapOf(date.plusDays(1) to 30),
        )
        assertEquals(listOf(schedule), ScheduleRepository.decode(stored))
        assertEquals(listOf(schedule), ScheduleRepository.decode(ScheduleRepository.encode(listOf(schedule))))
    }

    @Test
    fun `schedules from older versions load with defaults for newer fields`() {
        assertEquals(
            listOf(Schedule(id = 3, start = 60, end = 120)),
            ScheduleRepository.decode("""[{"id":3,"start":60,"end":120,"future":1}]"""),
        )
    }

    @Test
    fun `the running window's state still loads`() {
        val stored = """{"started":{"window":"2026-10-09T23:00","changes":1,"wanted":3,"already":0},""" +
            """"wifiKeptUntil":"2026-10-10T07:00"}"""
        val state = Engine.State(Started(window, airplane, wanted = both), LocalDateTime.of(2026, 10, 10, 7, 0))
        assertEquals(state, EngineRepository.decode(stored))
        assertEquals(state, EngineRepository.decode(EngineRepository.encode(state)))
    }

    @Test
    fun `the running window's state moves from the older separate keys`() {
        // Before 2.0, a window kept only what it changed, so the migration treats it as if it wanted only that.
        val keys = mapOf(
            "startedWindow" to "2026-10-09T23:00",
            "startedChanges" to 1,
            "startedWanted" to 3,
            "startedAlready" to 2,
            "wifiKeptUntil" to "2026-10-10T07:00",
        )
        assertEquals(
            Engine.State(Started(window, airplane, both, Changes(false, true)), LocalDateTime.of(2026, 10, 10, 7, 0)),
            EngineRepository.legacy(keys::get),
        )
        val beforeTwo = mapOf("startedWindow" to "2026-10-09T23:00", "startedChanges" to 3)
        assertEquals(Engine.State(Started(window, both)), EngineRepository.legacy(beforeTwo::get))
    }

    @Test
    fun `an unreadable running window's state is dropped rather than crashing every alarm`() {
        assertEquals(Engine.State(), EngineRepository.decode("{"))
        assertEquals(
            Engine.State(),
            EngineRepository.decode("""{"started":{"window":"soon","changes":1,"wanted":1,"already":0}}"""),
        )
    }
}
