package io.github.probecat.airplanescheduler.data

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineRepositoryTest {
    private val window = LocalDateTime.of(2026, 10, 9, 23, 0)
    private val both = Changes(airplane = true, wifi = true)
    private val airplane = Changes(airplane = true, wifi = false)

    @Test
    fun stateRoundTrips() {
        val state = Engine.State(
            Started(window, airplane, wanted = both, already = Changes(false, true)),
            window.plusHours(8),
        )
        assertEquals(state, EngineRepository.decode(EngineRepository.encode(state)))
        assertEquals(Engine.State(), EngineRepository.decode(EngineRepository.encode(Engine.State())))
    }

    @Test
    fun storedFormatStaysReadable() {
        val stored = """{"started":{"window":"2026-10-09T23:00","changes":1,"wanted":3,"already":0},""" +
            """"wifiKeptUntil":"2026-10-10T07:00"}"""
        assertEquals(
            Engine.State(Started(window, airplane, wanted = both), LocalDateTime.of(2026, 10, 10, 7, 0)),
            EngineRepository.decode(stored),
        )
    }

    @Test
    fun separateKeysFromOlderVersionsAreRead() {
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
        // Before 2.0 a window kept only what it changed.
        val old = mapOf("startedWindow" to "2026-10-09T23:00", "startedChanges" to 3)
        assertEquals(Engine.State(Started(window, both)), EngineRepository.legacy(old::get))
    }

    @Test
    fun unreadableStateIsDropped() {
        assertEquals(Engine.State(), EngineRepository.decode("{"))
        assertEquals(
            Engine.State(),
            EngineRepository.decode("""{"started":{"window":"soon","changes":1,"wanted":1,"already":0}}"""),
        )
    }
}
