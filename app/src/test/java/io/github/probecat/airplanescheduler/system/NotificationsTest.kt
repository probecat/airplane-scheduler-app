package io.github.probecat.airplanescheduler.system

import android.content.Intent
import io.github.probecat.airplanescheduler.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// What the posted notifications offer, and what their buttons do when the app receives them.
@RunWith(RobolectricTestRunner::class)
class NotificationsTest {
    private val device = Device()
    private val soon = Schedule(id = 1, start = device.minute(30), end = device.minute(90))

    @Test
    fun `Skip on the notice before a window skips it and takes the notice down`() {
        // The skipped notice would offer nothing, and the card has Undo.
        device.save(soon)
        val notice = device.notice(1)
        assertEquals(listOf(Notifier.POSTPONE, Notifier.SKIP), device.buttons(notice))
        device.tap(notice, Notifier.SKIP)
        assertEquals(1, device.schedules.single().skipped.size)
        assertNull(device.notice(1))
    }

    @Test
    fun `Postpone on a notice that is already out of date does nothing`() {
        // A window is postponed only once, even if the user taps the same button twice before the notice updates.
        device.save(soon)
        val notice = device.notice(1)
        device.tap(notice, Notifier.POSTPONE)
        device.tap(notice, Notifier.POSTPONE)
        assertEquals(listOf(30), device.schedules.single().delayed.values.toList())
        assertEquals(listOf(Notifier.SKIP), device.buttons(device.notice(1)))
    }

    @Test
    fun `an end that fails posts a problem whose Retry turns airplane mode off`() {
        // Retry switches only what the end failed to switch.
        val running = Schedule(id = 1, start = device.minute(-30), end = device.minute(30), disableWifi = false)
        device.save(running)
        assertTrue(device.airplaneOn)
        device.shizuku.works = false
        device.save(running.copy(end = device.minute(0)))
        assertTrue(device.airplaneOn)
        assertEquals(listOf(Notifier.RETRY), device.buttons(device.problem(1)))

        device.shizuku.works = true
        device.tap(device.problem(1), Notifier.RETRY)
        assertFalse(device.airplaneOn)
        assertNull(device.problem(1))
    }

    @Test
    fun `a window that ended while the phone was off is reported, not undone`() {
        // The user may have wanted airplane mode since, so only a tap on the problem turns it off.
        val running = Schedule(id = 1, start = device.minute(-30), end = device.minute(30), disableWifi = false)
        device.save(running)
        // As if the phone was off since this window's start yesterday.
        val state = device.app.engine.state
        device.app.engine.state = state.copy(started = state.started!!.let { it.copy(window = it.window.minusDays(1)) })
        device.alarm(Intent.ACTION_BOOT_COMPLETED)
        assertTrue(device.airplaneOn)
        assertNotNull(device.problem(1))
        assertNull(device.app.engine.state.started)
    }
}
