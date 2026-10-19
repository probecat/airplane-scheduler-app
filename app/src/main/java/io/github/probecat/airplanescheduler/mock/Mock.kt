package io.github.probecat.airplanescheduler.mock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.BuildConfig
import io.github.probecat.airplanescheduler.system.AlarmScheduler
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.Notifier
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class MockConfig(
    val enabled: Boolean = false,
    // The scenario applied last, by id.
    val scenario: String? = null,
    val shizuku: ShizukuState = ShizukuState.READY,
    val shizukuInstalled: Boolean = true,
    val notifications: Boolean = true,
    // As below Android 12 with battery optimization on for the app, whatever the real version.
    val batteryOptimized: Boolean = false,
    // Added to the real clock in whole minutes, so the mock clock keeps ticking on the minute.
    val clockOffset: Long = 0,
    // Null follows the system.
    val is24Hour: Boolean? = null,
    val locale: String? = null,
    val fontScale: Float? = null,
    // 0 is like Remove animations in the accessibility settings.
    val animationScale: Float? = null,
)

// Mock data lives in stores of its own and never switches anything: alarms are paused while it's on,
// and come back for the real schedules when it's off. Release builds never turn it on.
object Mock {
    private const val PREFS = "mock"
    private const val KEY = "config"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var prefs: SharedPreferences
    private val state = MutableStateFlow(MockConfig())

    val config: StateFlow<MockConfig> = state.asStateFlow()

    val active: Boolean get() = BuildConfig.DEBUG && state.value.enabled

    fun init(app: App) {
        if (!BuildConfig.DEBUG) return
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        state.value = try {
            prefs.getString(KEY, null)?.let { json.decodeFromString<MockConfig>(it) } ?: MockConfig()
        } catch (_: SerializationException) {
            MockConfig()
        }
        if (active) {
            useStores(app, true)
            app.refreshShizuku()
        }
    }

    fun update(app: App, transform: (MockConfig) -> MockConfig) {
        if (!BuildConfig.DEBUG) return
        val before = state.value
        val after = installedIfRunning(before, transform(before))
        state.value = after
        prefs.edit { putString(KEY, json.encodeToString(after)) }
        if (before.enabled != after.enabled) {
            useStores(app, after.enabled)
            AlarmScheduler.scheduleAll(app)
        }
        // A dismissed ready strip comes back, so each state shows its strip.
        if (after.enabled && before.shizuku != after.shizuku) {
            app.settings.update { it.copy(hideShizukuReady = false) }
        }
        app.refreshShizuku()
        if (before.enabled != after.enabled) Notifier.reset(app) else Notifier.refresh(app)
    }

    // Shizuku only runs when it's installed. Whichever of the two just changed decides the other.
    private fun installedIfRunning(before: MockConfig, after: MockConfig): MockConfig = when {
        after.shizukuInstalled || after.shizuku == ShizukuState.OFFLINE -> after
        before.shizukuInstalled -> after.copy(shizuku = ShizukuState.OFFLINE)
        else -> after.copy(shizukuInstalled = true)
    }

    private fun useStores(app: App, mock: Boolean) {
        app.schedules.useStore(mock)
        app.settings.useStore(mock)
    }

    fun now(): LocalDateTime = LocalDateTime.now().let { if (active) it.plusMinutes(state.value.clockOffset) else it }

    // The real time when the mock clock reads [time].
    fun real(time: LocalDateTime): LocalDateTime = if (active) time.minusMinutes(state.value.clockOffset) else time

    fun zonedNow(): ZonedDateTime =
        ZonedDateTime.now().let { if (active) it.plusMinutes(state.value.clockOffset) else it }

    // Moves the mock clock so it reads [target] now.
    fun offsetTo(target: LocalDateTime): Long =
        Duration.between(LocalDateTime.now().withSecond(0).withNano(0), target).toMinutes()

    // A mocked Shizuku that is installed but isn't on the device opens its repository instead.
    fun shizukuApp(real: Intent?): Intent? = when {
        !state.value.shizukuInstalled -> null
        real != null -> real
        else -> ShizukuStatus.repository()
    }
}

@Composable
fun activeMock(): MockConfig? {
    if (!BuildConfig.DEBUG) return null
    val config by Mock.config.collectAsState()
    return config.takeIf { it.enabled }
}
