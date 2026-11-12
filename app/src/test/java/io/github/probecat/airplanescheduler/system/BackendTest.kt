package io.github.probecat.airplanescheduler.system

import android.content.Context
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Backend
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Switching through Shizuku or root, as the user chose.
@RunWith(RobolectricTestRunner::class)
class BackendTest {
    private val device = Device()
    private val both = Changes(airplane = true, wifi = true)

    @Test
    fun `with root chosen, schedules switch through root and not Shizuku`() {
        // A user without Shizuku would otherwise miss every start.
        device.app.settings.update { it.copy(backend = Backend.ROOT) }
        device.save(Schedule(id = 1, start = device.minute(1), end = device.minute(60)))
        device.startsNow(1)
        device.alarm(AlarmScheduler.CHANGE)
        assertTrue(device.airplaneOn)
        assertTrue(device.root.switched.isNotEmpty())
        assertTrue(device.shizuku.switched.isEmpty())
    }

    @Test
    fun `settings saved before root support keep Shizuku`() {
        // Existing users must not find their schedules switched through root they never allowed.
        val prefs = device.app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { clear().putInt("postponeMinutes", 15) }
        assertEquals(Backend.SHIZUKU, SettingsRepository(device.app).current.backend)
        prefs.edit { putString("backend", "ROOT") }
        assertEquals(Backend.ROOT, SettingsRepository(device.app).current.backend)
    }

    @Test
    fun `a rooted phone without Shizuku starts with root and keeps it`() {
        val prefs = device.app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { clear() }
        assertEquals(Backend.ROOT, SettingsRepository(device.app) { Backend.ROOT }.current.backend)
        // Installing Shizuku later must not move schedules away from root.
        assertEquals(Backend.ROOT, SettingsRepository(device.app) { Backend.SHIZUKU }.current.backend)
    }

    @Test
    fun `root counts only the changes whose command succeeded`() {
        // What counts as switched is what the end undoes. Wi-Fi that failed to go off must not be turned on later.
        val root = RootSwitcher(device.app, {}, shell = listOf("sh", "-c", "echo 1 0; echo 2 1"))
        assertEquals(Changes(airplane = true, wifi = false), runBlocking { root.set(true, both) })
    }

    @Test
    fun `when su is denied, nothing is switched and root shows as not allowed`() {
        // A failed su is how the app learns that root access was taken away.
        RootSwitcher(device.app, {}, shell = listOf("sh", "-c")).let { runBlocking { it.request() } }
        val denied = RootSwitcher(device.app, {}, shell = listOf("sh", "-c", "exit 1"))
        assertEquals(AccessState.READY, denied.access().state)
        assertEquals(Changes.NONE, runBlocking { denied.set(true, both) })
        assertEquals(AccessState.PERMISSION_NEEDED, denied.access().state)
    }
}
