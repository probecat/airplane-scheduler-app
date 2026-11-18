package io.github.probecat.airplanescheduler.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val hideShizukuReady: Boolean = false,
    // How long Postpone and Pause wait. 0 turns them off.
    val postponeMinutes: Int = 30,
    val askedNotifications: Boolean = false,
    val backend: Backend = Backend.SHIZUKU,
)

// [firstBackend] picks the backend when none is saved yet. Choices the user made go in [prefs] and come back with a
// restore. Ones that depend on this device's permissions and access go in [device], so a restore picks and asks for
// them again. See [Prefs].
class SettingsRepository(context: Context, private val firstBackend: () -> Backend = { Backend.SHIZUKU }) {
    private val prefs = context.getSharedPreferences(Prefs.SETTINGS, Context.MODE_PRIVATE)
    private val device = Prefs.device(context, listOf(BACKEND, ASKED_NOTIFICATIONS, HIDE_SHIZUKU_READY))
    private val state = MutableStateFlow(read())

    init {
        // The first pick is saved, so it persists when Shizuku or root is installed later.
        if (!device.contains(BACKEND)) device.edit { putString(BACKEND, current.backend.name) }
    }

    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings get() = state.value

    // What a reset goes back to. The backend is picked again, as on the first start.
    fun defaults(): Settings = Settings(backend = firstBackend())

    fun update(transform: (Settings) -> Settings) {
        val settings = transform(state.value)
        state.value = settings
        prefs.edit {
            putString("theme", settings.theme.name)
            putBoolean("dynamicColor", settings.dynamicColor)
            putInt("postponeMinutes", settings.postponeMinutes)
        }
        device.edit {
            putBoolean(HIDE_SHIZUKU_READY, settings.hideShizukuReady)
            putBoolean(ASKED_NOTIFICATIONS, settings.askedNotifications)
            putString(BACKEND, settings.backend.name)
        }
    }

    private fun read() = Settings().let { default ->
        Settings(
            theme = prefs.getString("theme", null)
                ?.let { name -> ThemeMode.entries.find { it.name == name } } ?: default.theme,
            dynamicColor = prefs.getBoolean("dynamicColor", default.dynamicColor),
            hideShizukuReady = device.getBoolean(HIDE_SHIZUKU_READY, default.hideShizukuReady),
            postponeMinutes = prefs.getInt("postponeMinutes", default.postponeMinutes),
            askedNotifications = device.getBoolean(ASKED_NOTIFICATIONS, default.askedNotifications),
            backend = device.getString(BACKEND, null)
                ?.let { name -> Backend.entries.find { it.name == name } } ?: firstBackend(),
        )
    }

    private companion object {
        const val BACKEND = "backend"
        const val ASKED_NOTIFICATIONS = "askedNotifications"
        const val HIDE_SHIZUKU_READY = "hideShizukuReady"
    }
}
