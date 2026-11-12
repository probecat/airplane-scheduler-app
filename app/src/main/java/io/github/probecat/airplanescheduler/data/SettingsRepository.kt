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

// [firstBackend] picks the backend when none is saved yet.
class SettingsRepository(context: Context, firstBackend: () -> Backend = { Backend.SHIZUKU }) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read(firstBackend))

    init {
        // The first pick is saved, so it persists when Shizuku or root is installed later.
        if (!prefs.contains("backend")) prefs.edit { putString("backend", current.backend.name) }
    }

    val settings: StateFlow<Settings> = state.asStateFlow()

    val current: Settings get() = state.value

    fun update(transform: (Settings) -> Settings) {
        val settings = transform(state.value)
        state.value = settings
        prefs.edit {
            putString("theme", settings.theme.name)
            putBoolean("dynamicColor", settings.dynamicColor)
            putBoolean("hideShizukuReady", settings.hideShizukuReady)
            putInt("postponeMinutes", settings.postponeMinutes)
            putBoolean("askedNotifications", settings.askedNotifications)
            putString("backend", settings.backend.name)
        }
    }

    private fun read(firstBackend: () -> Backend) = Settings().let { default ->
        Settings(
            theme = prefs.getString("theme", null)
                ?.let { name -> ThemeMode.entries.find { it.name == name } } ?: default.theme,
            dynamicColor = prefs.getBoolean("dynamicColor", default.dynamicColor),
            hideShizukuReady = prefs.getBoolean("hideShizukuReady", default.hideShizukuReady),
            postponeMinutes = prefs.getInt("postponeMinutes", default.postponeMinutes),
            askedNotifications = prefs.getBoolean("askedNotifications", default.askedNotifications),
            backend = prefs.getString("backend", null)
                ?.let { name -> Backend.entries.find { it.name == name } } ?: firstBackend(),
        )
    }

    private companion object {
        const val PREFS = "settings"
    }
}
