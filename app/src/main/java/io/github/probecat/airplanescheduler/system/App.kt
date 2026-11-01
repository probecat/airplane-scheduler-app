package io.github.probecat.airplanescheduler.system

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.github.probecat.airplanescheduler.data.EngineRepository
import io.github.probecat.airplanescheduler.data.ScheduleRepository
import io.github.probecat.airplanescheduler.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class App : Application() {
    lateinit var schedules: ScheduleRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var engine: EngineRepository
        private set
    lateinit var switcher: Switcher
        private set

    // Lives longer than the screens, so work that a tap started finishes after the screen closes.
    private val scope = MainScope()

    private val shizukuState = MutableStateFlow(ShizukuStatus.current())
    val shizuku: StateFlow<ShizukuState> = shizukuState.asStateFlow()

    private val suiState = MutableStateFlow(ShizukuStatus.sui)
    val sui: StateFlow<Boolean> = suiState.asStateFlow()

    private val airplaneState = MutableStateFlow(false)
    val airplane: StateFlow<Boolean> = airplaneState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        schedules = ScheduleRepository(this)
        settings = SettingsRepository(this)
        engine = EngineRepository(this)
        switcher = ShizukuSwitcher(this)
        Notifier.createChannels(this)
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

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    fun finishAfter(receiver: BroadcastReceiver, block: suspend CoroutineScope.() -> Unit) {
        val result = receiver.goAsync()
        launch {
            try {
                block()
            } finally {
                result.finish()
            }
        }
    }

    fun refreshShizuku() {
        val state = ShizukuStatus.current()
        val sui = ShizukuStatus.sui
        if (state == shizukuState.value && sui == suiState.value) return
        shizukuState.value = state
        suiState.value = sui
        Notifier.refresh(this)
    }

    companion object {
        fun from(context: Context): App = context.applicationContext as App
    }
}
