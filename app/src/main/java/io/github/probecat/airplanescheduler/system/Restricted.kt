package io.github.probecat.airplanescheduler.system

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

// During a backup or restore, the system runs the app with a plain Application in place of App, so receivers cannot
// work. Returns App, or null after sending [intent] again a little later, when App runs. [again] is false for system
// broadcasts, which the app cannot send. Those are dropped: the alarms they would reset are still set, and App sets
// them again when it starts.
@SuppressLint("MissingPermission") // Checked by exactAlarmsAllowed.
fun appOrRetry(context: Context, intent: Intent, again: Boolean = true): App? {
    (context.applicationContext as? App)?.let { return it }
    if (!again) return null
    val pending = PendingIntent.getBroadcast(
        context,
        RETRY_REQUEST,
        Intent(intent),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val alarms = context.getSystemService(AlarmManager::class.java)
    val at = System.currentTimeMillis() + RETRY_MILLIS
    if (exactAlarmsAllowed(context)) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    } else {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
    return null
}

// A backup or restore of this app's small files takes about a second.
private const val RETRY_MILLIS = 5_000L

// Apart from the request codes of the regular alarms, so a retry does not replace them.
private const val RETRY_REQUEST = 100
