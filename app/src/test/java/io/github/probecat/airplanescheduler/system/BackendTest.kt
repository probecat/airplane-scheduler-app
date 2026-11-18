package io.github.probecat.airplanescheduler.system

import android.content.Context
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.data.Backend
import io.github.probecat.airplanescheduler.data.Prefs
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
    fun `root backend switches through root only`() {
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
    fun `pre-root settings keep Shizuku`() {
        // Existing users must not find their schedules switched through root they never allowed.
        val prefs = device.app.getSharedPreferences(Prefs.SETTINGS, Context.MODE_PRIVATE)
        device.app.getSharedPreferences(Prefs.DEVICE, Context.MODE_PRIVATE).edit { clear() }
        prefs.edit { clear().putInt("postponeMinutes", 15) }
        assertEquals(Backend.SHIZUKU, SettingsRepository(device.app).current.backend)
        prefs.edit { putString("backend", "ROOT") }
        assertEquals(Backend.ROOT, SettingsRepository(device.app).current.backend)
    }

    @Test
    fun `rooted phone without Shizuku starts and stays root`() {
        device.app.getSharedPreferences(Prefs.DEVICE, Context.MODE_PRIVATE).edit { clear() }
        assertEquals(Backend.ROOT, SettingsRepository(device.app) { Backend.ROOT }.current.backend)
        // Installing Shizuku later must not move schedules away from root.
        assertEquals(Backend.ROOT, SettingsRepository(device.app) { Backend.SHIZUKU }.current.backend)
    }

    @Test
    fun `reset picks backend again`() {
        val rooted = SettingsRepository(device.app) { Backend.ROOT }
        rooted.update { it.copy(backend = Backend.SHIZUKU, postponeMinutes = 15) }
        rooted.update { rooted.defaults() }
        assertEquals(Backend.ROOT, rooted.current.backend)
        assertEquals(30, rooted.current.postponeMinutes)
        assertEquals(Backend.ROOT, SettingsRepository(device.app) { Backend.SHIZUKU }.current.backend)
        val shizuku = SettingsRepository(device.app) { Backend.SHIZUKU }
        shizuku.update { shizuku.defaults() }
        assertEquals(Backend.SHIZUKU, shizuku.current.backend)
    }

    @Test
    fun `root counts only succeeded commands`() {
        // What counts as switched is what the end undoes. Wi-Fi that failed to go off must not be turned on later.
        val root = RootSwitcher(device.app, {}, shell = listOf("sh", "-c", "echo 1 0; echo 2 1"))
        assertEquals(Changes(airplane = true, wifi = false), runBlocking { root.set(true, both) })
    }

    @Test
    fun `denied su switches nothing and shows not allowed`() {
        // A failed su is how the app learns that root access was taken away.
        RootSwitcher(device.app, {}, shell = listOf("sh", "-c")).let { runBlocking { it.request() } }
        val denied = RootSwitcher(device.app, {}, shell = listOf("sh", "-c", "exit 1"))
        assertEquals(AccessState.READY, denied.access().state)
        assertEquals(Changes.NONE, runBlocking { denied.set(true, both) })
        assertEquals(AccessState.PERMISSION_NEEDED, denied.access().state)
    }
}
