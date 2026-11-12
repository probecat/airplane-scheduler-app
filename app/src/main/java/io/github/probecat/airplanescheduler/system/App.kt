package io.github.probecat.airplanescheduler.system

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import io.github.probecat.airplanescheduler.data.Backend
import io.github.probecat.airplanescheduler.data.EngineRepository
import io.github.probecat.airplanescheduler.data.EventLog
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
    lateinit var log: EventLog
        private set
    lateinit var shizukuSwitcher: Switcher
        @VisibleForTesting internal set
    lateinit var rootSwitcher: Switcher
        @VisibleForTesting internal set

    val switcher: Switcher
        get() = if (settings.current.backend == Backend.ROOT) rootSwitcher else shizukuSwitcher

    // Lives longer than the screens, so work that a tap started finishes after the screen closes.
    private val scope = MainScope()

    private val accessState = MutableStateFlow(Access(Access.Kind.SHIZUKU, AccessState.OFFLINE))
    val access: StateFlow<Access> = accessState.asStateFlow()

    private val airplaneState = MutableStateFlow(false)
    val airplane: StateFlow<Boolean> = airplaneState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        schedules = ScheduleRepository(this)
        settings = SettingsRepository(this, ::firstBackend)
        engine = EngineRepository(this)
        log = EventLog(this)
        shizukuSwitcher = ShizukuSwitcher(this)
        rootSwitcher = RootSwitcher(this, ::refreshAccess)
        accessState.value = switcher.access()
        Notifier.createChannels(this)
        // Restores alarms that a force stop removed.
        AlarmScheduler.scheduleAll(this)
        // Only the state follows Shizuku. A start or end that it missed is not caught up.
        Shizuku.addBinderReceivedListenerSticky(::refreshAccess)
        Shizuku.addBinderDeadListener(::refreshAccess)
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshAccess() }
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

    private fun firstBackend(): Backend {
        val shizuku = ShizukuStatus.sui || ShizukuStatus.launchIntent(this) != null
        return if (!shizuku && RootSwitcher.installed()) Backend.ROOT else Backend.SHIZUKU
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

    fun refreshAccess() {
        val access = switcher.access()
        if (access == accessState.value) return
        accessState.value = access
        log.add("Access changed: ${Events.access(access)}")
        Notifier.refresh(this)
    }

    companion object {
        fun from(context: Context): App = context.applicationContext as App
    }
}
