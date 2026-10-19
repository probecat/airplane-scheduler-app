package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndingTest {
    private val window = LocalDateTime.of(2026, 10, 9, 23, 0)
    private val both = Changes(airplane = true, wifi = true)
    private val airplane = Changes(airplane = true, wifi = false)

    @Test
    fun anEndThatUndidEverythingIsClean() {
        val ending = Ending.of(Started(window, both), both, both, airplaneOn = true, wifiOn = false, enableWifi = true)
        assertTrue(ending.clean)
        assertTrue(
            Ending.of(Started(window, both), airplane, airplane, true, false, enableWifi = false)
                .let { it.clean && it.wifiKeptOff },
        )
    }

    @Test
    fun whatCouldNotBeUndoneFails() {
        val ending = Ending.of(
            Started(window, both),
            both,
            airplane,
            airplaneOn = true,
            wifiOn = false,
            enableWifi = true,
        )
        assertEquals(Changes(airplane = false, wifi = true), ending.failed)
        assertFalse(ending.clean)
    }

    @Test
    fun whatWasAlreadySwitchedStays() {
        val started = Started(window, Changes.NONE, Changes.NONE, already = both)
        val ending = Ending.of(
            started,
            Changes.NONE,
            Changes.NONE,
            airplaneOn = true,
            wifiOn = false,
            enableWifi = true,
        )
        assertTrue(ending.airplaneWasOn && ending.wifiWasOff)
        // Wi-Fi that the schedule keeps off anyway isn't worth a mention.
        assertFalse(Ending.of(started, Changes.NONE, Changes.NONE, true, false, enableWifi = false).wifiWasOff)
    }

    @Test
    fun airplaneModeThatTheEndLeftOnStaysOn() {
        // A window started before 2.0 kept only its changes, so what the start found isn't known.
        val ending = Ending.of(Started(window, Changes.NONE), Changes.NONE, Changes.NONE, true, true, true)
        assertTrue(ending.airplaneStaysOn)
        assertFalse(ending.clean)
    }

    @Test
    fun airplaneModeTurnedOffDuringTheWindow() {
        val ending = Ending.of(Started(window, airplane), airplane, airplane, airplaneOn = false, wifiOn = true, true)
        assertTrue(ending.airplaneTurnedOff)
    }

    @Test
    fun aStartThatFailedIsReported() {
        val started = Started(window, Changes.NONE, wanted = both)
        assertTrue(Ending.of(started, Changes.NONE, Changes.NONE, airplaneOn = false, wifiOn = true, true).missedStart)
    }

    @Test
    fun aStartCaughtUpByHandStaysOn() {
        val started = Started(window, Changes.NONE, wanted = both)
        val ending = Ending.of(started, Changes.NONE, Changes.NONE, airplaneOn = true, wifiOn = true, true)
        assertFalse(ending.missedStart)
        assertTrue(ending.airplaneStaysOn)
    }
}
