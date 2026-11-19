package io.github.probecat.airplanescheduler.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

// A restore brings back only the files that the backup rules in res/xml list: what the user set up. State that
// depends on this device, such as permissions, access or a running schedule, goes in DEVICE or another unlisted file.
// A restore then starts it fresh, as a new install does.
object Prefs {
    const val SCHEDULES = "schedules"
    const val SETTINGS = "settings"
    const val DEVICE = "device"

    // Before v3.1, device state was kept in SETTINGS. Moves [keys] out of it once.
    fun device(context: Context, keys: Collection<String>): SharedPreferences {
        val device = context.getSharedPreferences(DEVICE, Context.MODE_PRIVATE)
        val settings = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val old = settings.all.filterKeys { it in keys }
        if (old.isEmpty()) return device
        // Written before the removal, so a crash in between loses nothing.
        device.edit(commit = true) {
            old.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                }
            }
        }
        settings.edit { old.keys.forEach(::remove) }
        return device
    }
}
