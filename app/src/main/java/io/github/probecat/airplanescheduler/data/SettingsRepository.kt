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
    val remindShizuku: Boolean = false,
    val hideShizukuReady: Boolean = false,
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
            putBoolean("remindShizuku", settings.remindShizuku)
            putBoolean("hideShizukuReady", settings.hideShizukuReady)
        }
    }

    // Debug builds keep mock settings in a store of their own, so the real ones stay untouched.
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
                    remove("startedAlready")
                } else {
                    putString("startedWindow", value.window.toString())
                    putInt("startedChanges", value.changes.bits)
                    putInt("startedAlready", value.already.bits)
                }
            }
        }

    private fun readStarted() = prefs.getString("startedWindow", null)?.let { window ->
        Boundary.Started(
            LocalDateTime.parse(window),
            Changes.of(prefs.getInt("startedChanges", 0)),
            Changes.of(prefs.getInt("startedAlready", 0)),
        )
    }

    private fun read() = Settings(
        theme = prefs.getString("theme", null)
            ?.let { name -> ThemeMode.entries.find { it.name == name } } ?: ThemeMode.SYSTEM,
        dynamicColor = prefs.getBoolean("dynamicColor", true),
        remindShizuku = prefs.getBoolean("remindShizuku", false),
        hideShizukuReady = prefs.getBoolean("hideShizukuReady", false),
    )

    private companion object {
        const val PREFS = "settings"
    }
}
