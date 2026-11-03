package io.github.probecat.airplanescheduler.system

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

object AlarmScheduler {
    const val CHANGE = "io.github.probecat.airplanescheduler.CHANGE"
    const val NOTICE = "io.github.probecat.airplanescheduler.NOTICE"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // Sets only the next alarm of each kind. Every alarm computes it again in local time, so clock and time-zone
    // changes stay correct.
    fun scheduleAll(context: Context) {
        val schedules = App.from(context).schedules.all
        val now = ZonedDateTime.now()
        set(context, CHANGE, ScheduleResolver.nextChange(schedules, now))
        set(context, NOTICE, ScheduleResolver.nextNotice(schedules, now, ScheduleResolver.AHEAD_MINUTES))
    }

    // The last alarm that this process set for each kind. Only a force stop or an update removes alarms, and both end
    // the process. So the process does not set the same alarm again, for example when an alarm starts the process and
    // the process then reschedules.
    @VisibleForTesting internal val last = ConcurrentHashMap<String, Pair<Long?, Boolean>>()

    // Setting the same intent again replaces its alarm, and a null time cancels it. Without exact alarms, Android
    // can run the alarm late, which is better than not at all.
    @SuppressLint("MissingPermission") // Checked by exactAlarmsAllowed.
    private fun set(context: Context, action: String, time: ZonedDateTime?) {
        val millis = time?.toInstant()?.toEpochMilli()
        val exact = exactAlarmsAllowed(context)
        if (last.put(action, millis to exact) == (millis to exact)) return
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, 0, intent, FLAGS)
        when {
            millis == null -> alarms.cancel(pending)
            exact -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
        // On Android 12, denying exact alarms drops them, so a second, inexact alarm keeps the changes coming.
        // The first alarm to fire sets both again, which replaces the other one.
        if (action == CHANGE && exactAlarmsDeniable) {
            val backup = PendingIntent.getBroadcast(context, 1, intent, FLAGS)
            if (millis == null) {
                alarms.cancel(backup)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, backup)
            }
        }
    }
}
