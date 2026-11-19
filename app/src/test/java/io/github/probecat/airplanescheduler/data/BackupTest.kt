package io.github.probecat.airplanescheduler.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.probecat.airplanescheduler.system.AccessState
import io.github.probecat.airplanescheduler.system.RootSwitcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// A restore brings back only the schedules and settings files. Device state left in them would come back stale, such
// as a running window that the app would then end on a phone where it never started.
@RunWith(RobolectricTestRunner::class)
class BackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before
    fun clear() = listOf(Prefs.SETTINGS, Prefs.SCHEDULES, Prefs.DEVICE).forEach {
        prefs(it).edit(commit = true) { clear() }
    }

    @Test
    fun `device state moves out of settings file intact`() {
        val engine = """{"started":{"window":"2026-10-09T23:00","changes":1,"wanted":3,"already":0}}"""
        prefs(Prefs.SETTINGS).edit(commit = true) {
            putString("theme", "DARK")
            putInt("postponeMinutes", 15)
            putString("backend", "ROOT")
            putBoolean("askedNotifications", true)
            putBoolean("hideShizukuReady", true)
            putBoolean("rootGranted", true)
            putString("engine", engine)
        }
        val settings = SettingsRepository(context).current
        val state = EngineRepository(context).state
        val root = RootSwitcher(context, {}).access().state

        val expected = Settings(
            ThemeMode.DARK,
            hideShizukuReady = true,
            postponeMinutes = 15,
            askedNotifications = true,
            backend = Backend.ROOT,
        )
        assertEquals(expected, settings)
        assertEquals(EngineRepository.decode(engine), state)
        assertEquals(AccessState.READY, root)
        assertEquals(setOf("theme", "postponeMinutes"), prefs(Prefs.SETTINGS).all.keys)
    }

    @Test
    fun `settings file only holds restored values`() {
        // A new setting fails here until it is placed. Device state goes in Prefs.DEVICE.
        val settings = SettingsRepository(context)
        settings.update {
            Settings(ThemeMode.DARK, false, true, 15, 45, true, Backend.ROOT, safeMode = false)
        }
        EngineRepository(context).state = EngineRepository.decode(
            """{"started":{"window":"2026-10-09T23:00","changes":1,"wanted":1,"already":0}}""",
        )
        runBlocking { RootSwitcher(context, {}, shell = listOf("sh", "-c")).request() }
        val restored = setOf("theme", "dynamicColor", "postponeMinutes", "alertMinutes", "safeMode")
        assertEquals(restored, prefs(Prefs.SETTINGS).all.keys)
    }
}
