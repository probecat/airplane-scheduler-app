package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

// Which alarms the app sets, and what it does when they come.
@RunWith(RobolectricTestRunner::class)
class AlarmsTest {
    private val device = Device()

    @Test
    fun `the start alarm switches what the schedule asks for`() {
        // A new schedule turns Wi-Fi off too.
        device.save(Schedule(id = 1, start = device.minute(1), end = device.minute(60)))
        device.startsNow(1)
        device.alarm(AlarmScheduler.CHANGE)
        assertTrue(device.airplaneOn)
        assertFalse(device.wifiOn)
        assertTrue(device.app.engine.state.started != null)
    }

    @Test
    fun `a start that Shizuku could not switch shows as missed`() {
        // Without Shizuku, the notice offers Skip only. Turn on would fail too.
        device.shizuku.works = false
        device.save(Schedule(id = 1, start = device.minute(1), end = device.minute(60)))
        device.startsNow(1)
        device.alarm(AlarmScheduler.CHANGE)
        assertFalse(device.airplaneOn)
        assertEquals(listOf(Notifier.SKIP), device.buttons(device.notice(1)))
    }

    @Test
    fun `saving sets the next start and the notice an hour before, and deleting removes them`() {
        val schedule = Schedule(id = 1, start = device.minute(90), end = device.minute(120))
        device.save(schedule)
        val start = startMillis(schedule)
        assertEquals(mapOf(AlarmScheduler.CHANGE to start, AlarmScheduler.NOTICE to start - 3_600_000), alarms())
        device.save()
        assertEquals(emptyMap<String, Long>(), alarms())
    }

    @Test
    @Config(sdk = [31])
    fun `on Android 12 with exact alarms denied, the start still has an alarm`() {
        // A late start is better than none.
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val schedule = Schedule(id = 1, start = device.minute(90), end = device.minute(120))
        device.save(schedule)
        assertEquals(startMillis(schedule), alarms()[AlarmScheduler.CHANGE])
    }

    @Suppress("DEPRECATION") // ScheduledAlarm has no getter for the intent.
    private fun alarms(): Map<String?, Long> =
        device.alarms().associate { shadowOf(it.operation).savedIntent.action to it.triggerAtMs }

    private fun startMillis(schedule: Schedule): Long {
        val today = LocalDate.now().atTime(schedule.start / 60, schedule.start % 60)
        val start = if (today.isAfter(LocalDateTime.now())) today else today.plusDays(1)
        return start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
