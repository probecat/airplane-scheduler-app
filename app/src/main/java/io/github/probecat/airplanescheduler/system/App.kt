package io.github.probecat.airplanescheduler.system

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.probecat.airplanescheduler.data.ScheduleRepository
import io.github.probecat.airplanescheduler.data.SettingsRepository
import io.github.probecat.airplanescheduler.mock.Mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class App : Application() {
    lateinit var schedules: ScheduleRepository
        private set
    lateinit var settings: SettingsRepository
        private set

    private val shizukuState = MutableStateFlow(ShizukuStatus.current())
    val shizuku: StateFlow<ShizukuState> = shizukuState.asStateFlow()

    private val airplaneState = MutableStateFlow(false)
    val airplane: StateFlow<Boolean> = airplaneState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        schedules = ScheduleRepository(this)
        settings = SettingsRepository(this)
        Notifier.createChannels(this)
        Mock.init(this)
        // Restores alarms that a force stop removed.
        AlarmScheduler.scheduleAll(this)
        // Only the state follows Shizuku. A start or end that it missed is not caught up.
        Shizuku.addBinderReceivedListenerSticky(::refreshShizuku)
        Shizuku.addBinderDeadListener(::refreshShizuku)
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshShizuku() }
        airplaneState.value = AirplaneController.airplaneOn(this)
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    airplaneState.value = AirplaneController.airplaneOn(this@App)
                    Notifier.refresh(this@App)
                }
            },
        )
    }

    fun refreshShizuku() {
        val state = ShizukuStatus.current()
        if (state == shizukuState.value) return
        shizukuState.value = state
        Notifier.refresh(this)
    }

    companion object {
        fun from(context: Context): App = context.applicationContext as App
    }
}
