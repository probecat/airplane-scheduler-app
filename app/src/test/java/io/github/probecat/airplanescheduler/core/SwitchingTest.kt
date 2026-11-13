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
    fun `a window turns airplane mode on and Wi-Fi off, then both back`() {
        // The plain case: the end reports what it turned back. The report is clean, so it clears itself.
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
    fun `Wi-Fi is left alone when the schedule doesn't turn it off`() {
        val phone = Phone(night.copy(disableWifi = false), now = friday(22))
        phone.passTo(friday(23))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        phone.passTo(saturday(7))
        assertFalse(phone.reports.single().second.wifiBackOn)
    }

    @Test
    fun `Wi-Fi that was already off at the start stays off at the end`() {
        // Even with "turn Wi-Fi back on" set, the app did not turn Wi-Fi off, so it does not turn it on. The report
        // says why.
        val phone = Phone(night, now = friday(22), wifiOn = false)
        phone.passTo(friday(23))
        phone.passTo(saturday(7))
        assertFalse(phone.wifiOn)
        assertTrue(phone.reports.single().second.wifiWasOff)
    }

    @Test
    fun `airplane mode that was already on at the start stays on at the end`() {
        // It is also not a missed start, so the card does not ask to turn it on.
        val phone = Phone(night, now = friday(22), airplaneOn = true)
        phone.passTo(friday(23, 5))
        assertNull(phone.missedStart())
        phone.passTo(saturday(7))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        assertTrue(phone.reports.single().second.airplaneWasOn)
    }

    @Test
    fun `a schedule with take over turns off an airplane mode it did not turn on`() {
        // With the option, the end also undoes an airplane mode that was already on at the start, and the report
        // says that it took it over instead of saying that it stays on.
        val phone = Phone(night.copy(turnOffIfAlreadyOn = true), now = friday(22), airplaneOn = true)
        phone.passTo(friday(23, 5))
        assertNull(phone.missedStart())
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        val ending = phone.reports.single().second
        assertTrue(ending.airplaneTakenOver)
        assertFalse(ending.airplaneWasOn)
    }

    @Test
    fun `Wi-Fi stays off at the end when the schedule doesn't turn it back on`() {
        val phone = Phone(night.copy(enableWifi = false), now = friday(22))
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertFalse(phone.wifiOn)
        assertTrue(phone.reports.single().second.clean)
    }

    @Test
    fun `airplane mode turned off during the window is reported, not a missed start`() {
        // The choice of the user wins. The card does not ask again, and the end report says that the user turned it
        // off.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        phone.airplaneOn = false
        phone.passTo(friday(23, 30))
        assertNull(phone.missedStart())
        phone.passTo(saturday(7))
        assertTrue(phone.reports.single().second.airplaneTurnedOff)
    }

    @Test
    fun `Wi-Fi switches while airplane mode is off`() {
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
    fun `a start Shizuku couldn't make is a missed start`() {
        // The card offers Turn on, and the end reports the missed start and switches nothing.
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
    fun `a start that is still switching, or whose alarm is due, is not a missed start`() {
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
    fun `Turn on catches up a missed start, and the end undoes it all`() {
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
    fun `an edit doesn't catch up a missed start`() {
        // Only Turn on does, so a save of an unrelated change cannot turn airplane mode on unexpectedly.
        val phone = Phone(night, now = friday(22))
        phone.shizukuWorks = false
        phone.passTo(friday(23, 30))
        phone.shizukuWorks = true
        phone.save(night.copy(name = "Sleep"))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `an end Shizuku couldn't make is reported with what failed, and Retry fixes it`() {
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
    fun `ending early is quiet when it works, unlike an end at its time`() {
        // The app already confirms the tap, so only a scheduled end posts a report.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        assertTrue(phone.skip())
        assertFalse(phone.airplaneOn)
        assertTrue(phone.reports.isEmpty())
    }

    @Test
    fun `a window whose end passed while the phone was off is forgotten, not undone`() {
        // Airplane mode that turns off hours late surprises the user, so the app only reports what is still switched.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23))
        phone.rebootAt(saturday(12))
        assertTrue(phone.airplaneOn)
        assertEquals(listOf(both), phone.leftSwitched)
        assertNull(phone.state.started)
        // The next night starts as usual.
        phone.airplaneOn = false
        phone.wifiOn = true
        phone.passTo(saturday(23))
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `a forgotten window reports only what is still switched`() {
        // What the user already turned back needs no report.
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
    fun `a window whose end alarm never came is forgotten at the next alarm`() {
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
    fun `a reboot during the window keeps it running`() {
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
    fun `skipping the second of touching windows ends the first on time`() {
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
    fun `switching off or deleting the running schedule ends its window now`() {
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
    fun `an edit to the running schedule's end option applies at its end`() {
        // The saved option wins over the one at the start.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.save(night.copy(enableWifi = false))
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `switching on a schedule whose window covers now starts it now`() {
        val phone = Phone(night.copy(enabled = false), now = friday(23, 30))
        phone.setEnabled(night.id, true)
        assertTrue(phone.airplaneOn)
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `moving the running window's start keeps it running and its end undoes it`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.save(night.copy(start = at(22)))
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
        assertTrue(phone.wifiOn)
    }

    @Test
    fun `moving the running window's end before now ends it now`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.save(night.copy(end = at(23, 15)))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `ending a window while its start is still switching turns back what the start made`() {
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
    fun `pausing ends the window now and starts it again when the pause is over`() {
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
    fun `undoing an early end starts the window again`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.skip()
        assertTrue(phone.undo())
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `Wi-Fi turned on during a window stays on when it starts again before its end`() {
        // Someone wanted Wi-Fi, so a resumed pause or an undone early end does not turn it off again.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.wifiOn = true
        phone.postpone(minutes = 15)
        phone.passTo(friday(23, 45))
        assertTrue(phone.airplaneOn)
        assertTrue(phone.wifiOn)
        // The next window turns it off again.
        phone.passTo(saturday(23))
        assertFalse(phone.wifiOn)
    }

    @Test
    fun `Wi-Fi is turned off again when nobody turned it on`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        phone.postpone(minutes = 15)
        phone.passTo(friday(23, 45))
        assertFalse(phone.wifiOn)
    }
}
