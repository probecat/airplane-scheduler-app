package io.github.probecat.airplanescheduler.data

import android.content.Context
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hideShizukuReady: Boolean = false,
    // How long Postpone and Pause wait. 0 turns them off.
    val postponeMinutes: Int = 30,
    // Whether saving the first schedule asked for notifications.
    val askedNotifications: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private var prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings get() = state.value

    private val startedState = MutableStateFlow(readStarted())

    val startedWindow: StateFlow<Boundary.Started?> = startedState.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val settings = transform(state.value)
        state.value = settings
        prefs.edit {
            putString("theme", settings.theme.name)
            putBoolean("dynamicColor", settings.dynamicColor)
            putBoolean("hideShizukuReady", settings.hideShizukuReady)
            putInt("postponeMinutes", settings.postponeMinutes)
            putBoolean("askedNotifications", settings.askedNotifications)
        }
    }

    fun useStore(mock: Boolean) {
        prefs = context.getSharedPreferences(if (mock) "mock_$PREFS" else PREFS, Context.MODE_PRIVATE)
        state.value = read()
        startedState.value = readStarted()
    }

    var started: Boundary.Started?
        get() = startedState.value
        set(value) {
            startedState.value = value
            prefs.edit {
                if (value == null) {
                    remove("startedWindow")
                    remove("startedChanges")
                    remove("startedWanted")
                    remove("startedAlready")
                } else {
                    putString("startedWindow", value.window.toString())
                    putInt("startedChanges", value.changes.bits)
                    putInt("startedWanted", value.wanted.bits)
                    putInt("startedAlready", value.already.bits)
                }
            }
        }

    // A window started before 2.0 kept only its changes.
    private fun readStarted() = prefs.getString("startedWindow", null)?.let { window ->
        val changes = prefs.getInt("startedChanges", 0)
        Boundary.Started(
            LocalDateTime.parse(window),
            Changes.of(changes),
            wanted = Changes.of(prefs.getInt("startedWanted", changes)),
            already = Changes.of(prefs.getInt("startedAlready", 0)),
        )
    }

    private fun read() = Settings(
        theme = prefs.getString("theme", null)
            ?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM,
        dynamicColor = prefs.getBoolean("dynamicColor", true),
        hideShizukuReady = prefs.getBoolean("hideShizukuReady", false),
        postponeMinutes = prefs.getInt("postponeMinutes", 30),
        askedNotifications = prefs.getBoolean("askedNotifications", false),
    )

    private companion object {
        const val PREFS = "settings"
    }
}
