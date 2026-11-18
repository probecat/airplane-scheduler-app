package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// What the app switches at the start and the end of a window, and what it reports. The app undoes only what it changed
// itself. It never catches up a missed start or end by itself.
class SwitchingTest {
    private val both = Changes(airplane = true, wifi = true)
    private val airplaneOnly = Changes(airplane = true, wifi = false)

    @Test
    fun `window turns airplane on and Wi-Fi off and then both back`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertEquals(listOf(night to Ending(Changes.NONE, wifiBackOn = true)), phone.reports)
        assertTrue(phone.reports.single().second.clean)
    }

    @Test
    fun `Wi-Fi untouched when not set to turn off`() {
        val phone = Phone(night.copy(disableWifi = false), now = friday(22))
        phone.passTo(friday(23))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        phone.passTo(saturday(7))
        assertFalse(phone.reports.single().second.wifiBackOn)
    }

    @Test
    fun `Wi-Fi off before start stays off at end`() {
        val phone = Phone(night, now = friday(22), wifiOn = false)
        phone.passTo(friday(23))
        phone.passTo(saturday(7))
        assertFalse(phone.wifiOn)
        assertTrue(phone.reports.single().second.wifiWasOff)
    }

    @Test
    fun `airplane on before start stays on at end`() {
        val phone = Phone(night, now = friday(22), airplaneOn = true)
        phone.passTo(friday(23, 5))
        assertNull(phone.missedStart())
        phone.passTo(saturday(7))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertTrue(phone.reports.single().second.airplaneWasOn)
    }

    @Test
    fun `Wi-Fi stays off when not set to turn back on`() {
        val phone = Phone(night.copy(enableWifi = false), now = friday(22))
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        assertTrue(phone.reports.single().second.clean)
    }

    @Test
    fun `airplane turned off mid-window is reported but not missed`() {
        // The choice of the user wins.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        phone.airplaneOn = false
        phone.passTo(friday(23, 30))
        assertNull(phone.missedStart())
        phone.passTo(saturday(7))
        assertTrue(phone.reports.single().second.airplaneTurnedOff)
    }

    @Test
    fun `Wi-Fi switches while airplane is off`() {
        // Android keeps the Wi-Fi state across airplane mode. So Wi-Fi goes off before airplane mode turns on, and
        // comes back on only after airplane mode turns off.
        assertEquals(
            listOf(ConnectivityPlan.WIFI, ConnectivityPlan.AIRPLANE),
            ConnectivityPlan.order(enabled = true, both),
        )
        assertEquals(
            listOf(ConnectivityPlan.AIRPLANE, ConnectivityPlan.WIFI),
            ConnectivityPlan.order(enabled = false, both),
        )
    }

    @Test
    fun `start Shizuku failed is missed start`() {
        val phone = Phone(night, now = friday(22))
        phone.shizukuWorks = false
        phone.passTo(friday(23))
        assertEquals(night, phone.missedStart())
        assertTrue(ScheduleStatus.Action.TURN_ON in phone.status()!!.actions)
        phone.shizukuWorks = true
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.reports.single().second.missedStart)
    }

    @Test
    fun `switching or due start is not missed`() {
        // Shizuku can take seconds and alarms can come late. In that time, the card must not flash "missed".
        val late = Phone(night, now = friday(23))
        assertNull(late.missedStart())
        late.now = friday(23, 1)
        assertEquals(night, late.missedStart())

        val phone = Phone(night, now = friday(22))
        phone.holdSwitches = true
        phone.passTo(friday(23, 2))
        assertNull(phone.missedStart())
        phone.finishSwitches()
        assertTrue(phone.airplaneOn)
        assertNull(phone.missedStart())
    }

    @Test
    fun `Turn on catches up missed start and end undoes all`() {
        val phone = Phone(night, now = friday(22))
        phone.shizukuWorks = false
        phone.passTo(friday(23, 30))
        phone.shizukuWorks = true
        assertTrue(phone.turnOn())
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertEquals(Ending(Changes.NONE, wifiBackOn = true), phone.reports.single().second)
    }

    @Test
    fun `edit does not catch up missed start`() {
        // Only Turn on does, so a save of an unrelated change cannot turn airplane mode on unexpectedly.
        val phone = Phone(night, now = friday(22))
        phone.shizukuWorks = false
        phone.passTo(friday(23, 30))
        phone.shizukuWorks = true
        phone.save(night.copy(name = "Sleep"))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `failed end reports failures and Retry fixes`() {
        // The app reports it even when an early end caused it, because airplane mode is still on.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.shizukuWorks = false
        phone.skip()
        assertTrue(phone.airplaneOn)
        val ending = phone.reports.single().second
        assertEquals(both, ending.failed)
        phone.shizukuWorks = true
        assertEquals(Ending(Changes.NONE, wifiBackOn = true), phone.retry(ending.failed))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `early end quiet when it works`() {
        // The app already confirms the tap, so only a scheduled end posts a report.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        assertTrue(phone.skip())
        assertFalse(phone.airplaneOn)
        assertTrue(phone.reports.isEmpty())
    }

    @Test
    fun `end passed while phone off is forgotten but not undone`() {
        // Airplane mode that turns off hours late surprises the user, so the app only reports what is still switched.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        phone.rebootAt(saturday(12))
        assertTrue(phone.airplaneOn)
        assertEquals(listOf(both), phone.leftSwitched)
        assertNull(phone.state.started)
        phone.airplaneOn = false
        phone.wifiOn = true
        phone.passTo(saturday(23))
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `forgotten window reports only what is still switched`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        phone.wifiOn = true
        phone.rebootAt(saturday(12))
        assertEquals(listOf(airplaneOnly), phone.leftSwitched)

        val fixed = Phone(night, now = friday(22))
        fixed.passTo(friday(23))
        fixed.airplaneOn = false
        fixed.wifiOn = true
        fixed.rebootAt(saturday(12))
        assertTrue(fixed.leftSwitched.isEmpty())
    }

    @Test
    fun `missing end alarm forgotten at next alarm`() {
        // As when Android holds alarms back: a later alarm for another schedule does not undo it either.
        val lunch = Schedule(id = 2, start = at(12), end = at(13))
        val phone = Phone(night, lunch, now = friday(22))
        phone.passTo(friday(23))
        phone.now = saturday(12, 30)
        phone.passTo(saturday(13))
        assertTrue(phone.airplaneOn)
        assertEquals(listOf(both), phone.leftSwitched)
    }

    @Test
    fun `reboot mid-window keeps it running`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        phone.rebootAt(saturday(3))
        assertTrue(phone.leftSwitched.isEmpty())
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `touching windows run as one`() {
        // Nothing switches where the windows meet. Wi-Fi follows the start option of the first window and the end
        // option of the last window.
        val morning =
            Schedule(id = 2, name = "Morning", start = at(7), end = at(9), disableWifi = false, enableWifi = false)
        val phone = Phone(night, morning, now = friday(22))
        phone.passTo(friday(23))
        assertFalse(phone.wifiOn)
        phone.passTo(saturday(8))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.reports.isEmpty())
        phone.passTo(saturday(9))
        assertFalse(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        assertEquals(morning, phone.reports.single().first)
    }

    @Test
    fun `skipping second touching window ends first on time`() {
        val morning = Schedule(id = 2, start = at(7), end = at(9))
        val phone = Phone(night, morning, now = friday(22))
        phone.passTo(saturday(6, 30))
        assertTrue(phone.skip(id = 2))
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertEquals(night, phone.reports.single().first)
    }

    @Test
    fun `disabling or deleting running schedule ends it now`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.setEnabled(night.id, false)
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)

        val deleted = Phone(night, now = friday(22))
        deleted.passTo(friday(23, 30))
        deleted.delete(night.id)
        assertFalse(deleted.airplaneOn)
    }

    @Test
    fun `end option edit applies at end`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.save(night.copy(enableWifi = false))
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `enabling schedule covering now starts it`() {
        val phone = Phone(night.copy(enabled = false), now = friday(23, 30))
        phone.setEnabled(night.id, true)
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `moving running start keeps it running`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.save(night.copy(start = at(22)))
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
    }

    @Test
    fun `moving running end before now ends it`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.save(night.copy(end = at(23, 15)))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `ending during start switch reverts start`() {
        // Without this, the late answer from Shizuku leaves airplane mode on after the window ended.
        val phone = Phone(night, now = friday(22))
        phone.holdSwitches = true
        phone.passTo(friday(23))
        phone.holdSwitches = false
        phone.setEnabled(night.id, false)
        phone.finishSwitches()
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertNull(phone.state.started)
    }

    @Test
    fun `pause ends now and restarts after`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 40))
        assertTrue(phone.postpone(minutes = 30))
        assertFalse(phone.airplaneOn)
        phone.passTo(saturday(0, 9))
        assertFalse(phone.airplaneOn)
        phone.passTo(saturday(0, 10))
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `undoing early end restarts window`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.skip()
        assertTrue(phone.undo())
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `Wi-Fi turned on mid-window stays on after restart`() {
        // Someone wanted Wi-Fi, so a resumed pause or an undone early end does not turn it off again.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.wifiOn = true
        phone.postpone(minutes = 15)
        phone.passTo(friday(23, 45))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        phone.passTo(saturday(23))
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `Wi-Fi off again when nobody turned it on`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.postpone(minutes = 15)
        phone.passTo(friday(23, 45))
        assertFalse(phone.wifiOn)
    }
}
