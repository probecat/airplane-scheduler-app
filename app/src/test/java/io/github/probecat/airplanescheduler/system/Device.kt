package io.github.probecat.airplanescheduler.system

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleTime
import io.github.probecat.airplanescheduler.data.Backend
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

// The real app on Robolectric. Times are real, so schedules are set relative to now. Coroutines run only on [idle], as
// the main thread would run them after the current tap.
class Device {
    val app: App = ApplicationProvider.getApplicationContext()
    val shizuku = FakeSwitcher(Access.Kind.SHIZUKU)
    val root = FakeSwitcher(Access.Kind.ROOT)
    private val notifications = app.getSystemService(NotificationManager::class.java)
    private val wifi = app.getSystemService(WifiManager::class.java)

    init {
        // The test runner can have su, which makes root the default backend.
        app.settings.update { it.copy(backend = Backend.SHIZUKU) }
        app.shizukuSwitcher = shizuku
        app.rootSwitcher = root
        app.refreshAccess()
        AlarmScheduler.last.clear()
        Notifier.forgetPosts()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    var airplaneOn: Boolean
        get() = AirplaneController.airplaneOn(app)
        set(value) {
            Settings.Global.putInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (value) 1 else 0)
        }

    @Suppress("DEPRECATION")
    var wifiOn: Boolean
        get() = wifi.isWifiEnabled
        set(value) {
            wifi.isWifiEnabled = value
        }

    val schedules: List<Schedule> get() = app.schedules.all

    fun minute(offset: Long): Int = ScheduleTime.minuteOf(LocalDateTime.now().plusMinutes(offset))

    fun save(vararg schedules: Schedule) {
        ScheduleUpdater.update(app, schedules.toList())
        idle()
    }

    fun idle() = shadowOf(Looper.getMainLooper()).idle()

    fun alarm(action: String) {
        app.sendBroadcast(Intent(app, AlarmReceiver::class.java).setAction(action))
        idle()
    }

    fun notice(id: Long): Notification? = shadowOf(notifications).getNotification("schedule", id.toInt())

    fun problem(id: Long): Notification? = shadowOf(notifications).getNotification("problem", id.toInt())

    fun buttons(notification: Notification?): List<String?> =
        notification?.actions.orEmpty().map { shadowOf(it.actionIntent).savedIntent.action }

    fun tap(notification: Notification?, button: String) {
        notification!!.actions.first { shadowOf(it.actionIntent).savedIntent.action == button }.actionIntent.send()
        idle()
    }

    // Moves a saved schedule as if its start time just came, without telling the app.
    fun startsNow(id: Long) {
        app.schedules.replaceAll(schedules.map { if (it.id == id) it.copy(start = minute(0)) else it })
    }

    fun alarms(): List<ShadowAlarmManager.ScheduledAlarm> =
        shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms

    inner class FakeSwitcher(private val kind: Access.Kind) : Switcher {
        var works = true
        val switched = mutableListOf<Pair<Boolean, Changes>>()

        override fun access() = Access(kind, if (works) AccessState.READY else AccessState.PERMISSION_NEEDED)

        override suspend fun request() = works

        override suspend fun connect(tap: Boolean) = works

        override suspend fun set(enabled: Boolean, changes: Changes): Changes {
            if (!works) return Changes.NONE
            switched += enabled to changes
            if (changes.airplane) airplaneOn = enabled
            if (changes.wifi) wifiOn = !enabled
            return changes
        }
    }
}
