package io.github.probecat.airplanescheduler.system

import android.app.Application
import android.content.Context
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

    override fun onCreate() {
        super.onCreate()
        schedules = ScheduleRepository(this)
        settings = SettingsRepository(this)
        Mock.init(this)
        // Restores alarms that a force stop removed.
        AlarmScheduler.scheduleAll(this)
        // Only the state follows Shizuku. A start or end that it missed is not caught up.
        Shizuku.addBinderReceivedListenerSticky(::refreshShizuku)
        Shizuku.addBinderDeadListener(::refreshShizuku)
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshShizuku() }
    }

    fun refreshShizuku() {
        val state = ShizukuStatus.current()
        shizukuState.value = state
        if (state == ShizukuState.READY) {
            ShizukuReminder.dismiss(this)
        }
    }

    companion object {
        fun from(context: Context): App = context.applicationContext as App
    }
}
