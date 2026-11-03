package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.ui.AppViewModel
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Taps in the app, before the main thread runs the switch that they start.
@RunWith(RobolectricTestRunner::class)
class TapsTest {
    private val device = Device()
    private val viewModel = AppViewModel(device.app)

    @Test
    fun `a saved schedule is there as soon as Save returns`() {
        // The screen tells when the new schedule starts, and the next new schedule needs a new id.
        val schedule = viewModel.newSchedule()
        assertNull(viewModel.save(schedule))
        assertEquals(listOf(schedule.id), device.schedules.map { it.id })
        assertTrue(viewModel.newSchedule().id != schedule.id)
    }

    @Test
    fun `a double tap on Postpone postpones once`() {
        // The second tap was offered for the state before the first one, so it does nothing.
        device.save(Schedule(id = 1, start = device.minute(30), end = device.minute(90)))
        val offered = ScheduleUpdater.status(device.app, device.schedules.single(), LocalDateTime.now())!!
        assertTrue(viewModel.postpone(1, offered))
        assertFalse(viewModel.postpone(1, offered))
        assertEquals(listOf(30), device.schedules.single().delayed.values.toList())
    }
}
