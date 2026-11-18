package io.github.probecat.airplanescheduler.system

import android.content.Intent
import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
    fun `notice Skip skips and removes notice`() {
        // The skipped notice would offer nothing, and the card has Undo.
        device.save(soon)
        val notice = device.notice(1)
        assertEquals(listOf(Notifier.POSTPONE, Notifier.SKIP), device.buttons(notice))
        device.tap(notice, Notifier.SKIP)
        assertEquals(1, device.schedules.single().skipped.size)
        assertNull(device.notice(1))
    }

    @Test
    fun `stale Postpone does nothing`() {
        device.save(soon)
        val notice = device.notice(1)
        device.tap(notice, Notifier.POSTPONE)
        device.tap(notice, Notifier.POSTPONE)
        assertEquals(listOf(30), device.schedules.single().delayed.values.toList())
        assertEquals(listOf(Notifier.SKIP), device.buttons(device.notice(1)))
    }

    @Test
    fun `failed end posts problem and Retry turns airplane off`() {
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
    fun `end while phone off is reported but not undone`() {
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

    @Test
    fun `end while phone off catches up with Safe mode off`() {
        device.app.settings.update { it.copy(safeMode = false) }
        val ended = Schedule(id = 1, start = device.minute(-90), end = device.minute(-30), disableWifi = false)
        device.save(ended)
        // As if the phone was off since the start of this window, which the app started.
        val start = LocalDateTime.now().minusMinutes(90).truncatedTo(ChronoUnit.MINUTES)
        device.airplaneOn = true
        device.app.engine.state = Engine.State(Started(start, Changes(airplane = true, wifi = false)))
        device.shizuku.works = false
        device.alarm(Intent.ACTION_BOOT_COMPLETED)
        assertTrue(device.airplaneOn)
        assertNotNull(device.problem(1))

        device.shizuku.works = true
        device.app.refreshAccess()
        device.idle()
        assertFalse(device.airplaneOn)
        assertNull(device.problem(1))
    }
}
