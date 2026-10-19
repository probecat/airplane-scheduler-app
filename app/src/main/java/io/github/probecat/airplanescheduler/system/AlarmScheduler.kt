package io.github.probecat.airplanescheduler.system

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.mock.Mock
import java.time.ZonedDateTime

object AlarmScheduler {
    const val CHANGE = "io.github.probecat.airplanescheduler.CHANGE"
    const val NOTICE = "io.github.probecat.airplanescheduler.NOTICE"
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    // Only the next alarm of each kind is set, recomputed in local time on every alarm so clock and
    // time-zone changes stay correct.
    fun scheduleAll(context: Context) {
        if (Mock.active) {
            set(context, CHANGE, null)
            set(context, NOTICE, null)
            return
        }
        val schedules = App.from(context).schedules.all
        val now = ZonedDateTime.now()
        set(context, CHANGE, ScheduleResolver.nextChange(schedules, now))
        set(context, NOTICE, ScheduleResolver.nextNotice(schedules, now, ScheduleResolver.AHEAD_MINUTES))
    }

    // Setting the same intent again replaces its alarm; no time cancels it. Without exact alarms, Android may
    // run it late, which beats not at all.
    @SuppressLint("MissingPermission") // Checked by exactAlarmsAllowed.
    private fun set(context: Context, action: String, time: ZonedDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, 0, intent, FLAGS)
        val millis = time?.toInstant()?.toEpochMilli()
        when {
            millis == null -> alarms.cancel(pending)
            exactAlarmsAllowed(context) -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
        // Denying exact alarms on Android 12 drops them, so an inexact twin keeps changes coming. The first
        // to fire sets both again, which replaces the other.
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
