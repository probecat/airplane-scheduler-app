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

    // Setting the same intent again replaces its alarm; no time cancels it.
    @SuppressLint("MissingPermission") // USE_EXACT_ALARM is granted at install.
    private fun set(context: Context, action: String, time: ZonedDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, 0, intent, FLAGS)
        if (time == null) {
            alarms.cancel(pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.toInstant().toEpochMilli(), pending)
        }
    }
}
