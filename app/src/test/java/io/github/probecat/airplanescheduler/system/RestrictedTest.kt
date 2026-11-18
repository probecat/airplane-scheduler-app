package io.github.probecat.airplanescheduler.system

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// During a backup or restore, the system runs a plain Application. A receiver that crashed then would cancel the
// restore, and an alarm or tap that it dropped would miss its switch.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestrictedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    private fun alarm(action: String) =
        AlarmReceiver().onReceive(context, Intent(context, AlarmReceiver::class.java).setAction(action))

    private fun tap(action: String) =
        NotificationReceiver().onReceive(context, Intent(context, NotificationReceiver::class.java).setAction(action))

    @Suppress("DEPRECATION") // ScheduledAlarm has no getter for the intent.
    private fun actions() = alarms.scheduledAlarms.mapNotNull { shadowOf(it.operation).savedIntent.action }

    @Test
    fun `receivers do not crash and cancel restore`() {
        alarm(Intent.ACTION_TIME_CHANGED)
        tap(Notifier.DISMISSED)
    }

    @Test
    fun `alarm or tap returns seconds later`() {
        alarm(AlarmScheduler.CHANGE)
        tap(Notifier.END)
        assertEquals(listOf(AlarmScheduler.CHANGE, Notifier.END), actions().sorted())
        assertTrue(alarms.scheduledAlarms.all { it.triggerAtMs - System.currentTimeMillis() in 1..10_000 })
    }
}
