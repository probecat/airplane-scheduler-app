package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// With Safe mode off, an end switches to what the schedule asks for, and the app catches up what it missed once it
// can. A radio that the user switched since stays as the user left it.
class SafeModeOffTest {
    private fun phone(
        vararg schedules: Schedule,
        now: LocalDateTime = friday(22),
        airplaneOn: Boolean = false,
        wifiOn: Boolean = true,
    ) = Phone(*schedules, now = now, airplaneOn = airplaneOn, wifiOn = wifiOn).apply { safe = false }

    @Test
    fun `end switches to schedule end whatever was set before`() {
        val phone = phone(night, airplaneOn = true, wifiOn = false)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertTrue(phone.reports.single().second.clean)
    }

    @Test
    fun `Wi-Fi untouched without Wi-Fi off at start`() {
        // The sheet greys out "Wi-Fi on at end" then, but keeps its value.
        val phone = phone(night.copy(disableWifi = false, enableWifi = true), wifiOn = false)
        phone.passTo(saturday(7))
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `early end switches like scheduled end`() {
        val phone = phone(night, airplaneOn = true)
        phone.passTo(friday(23, 30))
        assertTrue(phone.skip())
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `end without start record switches nothing`() {
        // As for a second alarm for the same end, or one that comes late.
        val phone = phone(night, now = saturday(6), airplaneOn = true, wifiOn = false)
        phone.passTo(saturday(7))
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        assertTrue(phone.reports.isEmpty())
    }

    @Test
    fun `end missed while phone off catches up with Shizuku`() {
        // Shizuku does not start with the phone, so the end waits for it.
        val phone = phone(night)
        phone.passTo(friday(23))
        phone.shizukuWorks = false
        phone.rebootAt(saturday(8))
        assertTrue(phone.airplaneOn)
        phone.accessReturns()
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertNull(phone.state.owed)
        assertEquals(Ending(Changes.NONE, wifiBackOn = true), phone.reports.last().second)
    }

    @Test
    fun `missed start catches up with Shizuku`() {
        val phone = phone(night)
        phone.shizukuWorks = false
        phone.rebootAt(friday(23, 30))
        assertFalse(phone.airplaneOn)
        phone.accessReturns()
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
    }

    @Test
    fun `next window replaces failed end`() {
        val phone = phone(night)
        phone.passTo(friday(23))
        phone.shizukuWorks = false
        phone.passTo(saturday(23, 30))
        phone.accessReturns()
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `Wi-Fi turned on stays on when start catches up`() {
        // The app was not running to see it, so it compares with what it saw when the start failed.
        val phone = phone(night, wifiOn = false)
        phone.shizukuWorks = false
        phone.passTo(friday(23))
        phone.wifiOn = true
        phone.accessReturns()
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
    }

    @Test
    fun `airplane mode switched back stays on when end catches up`() {
        // Off and on again looks unchanged, so the app counts what it saw while running.
        val phone = phone(night)
        phone.passTo(friday(23))
        phone.shizukuWorks = false
        phone.passTo(saturday(7))
        phone.userSetsAirplane(false)
        phone.userSetsAirplane(true)
        phone.accessReturns()
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
    }

    @Test
    fun `turning off Safe mode mid-window applies at end`() {
        val phone = Phone(night, now = friday(22), airplaneOn = true)
        phone.passTo(friday(23, 30))
        phone.safe = false
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `turning off Safe mode does not catch up what it missed`() {
        val phone = Phone(night, now = friday(22))
        phone.shizukuWorks = false
        phone.passTo(friday(23))
        phone.safe = false
        phone.accessReturns()
        assertFalse(phone.airplaneOn)
        assertEquals(night, phone.missedStart())
    }

    @Test
    fun `failed early end catches up`() {
        // The start answers late, after the early end failed, and must not drop what the end owes.
        val phone = phone(night, airplaneOn = true)
        phone.holdSwitches = true
        phone.passTo(friday(23))
        phone.holdSwitches = false
        phone.shizukuWorks = false
        assertTrue(phone.skip())
        phone.finishSwitches()
        assertTrue(phone.airplaneOn)
        phone.accessReturns()
        assertFalse(phone.airplaneOn)
    }
}
