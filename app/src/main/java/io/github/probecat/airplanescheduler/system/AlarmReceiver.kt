package io.github.probecat.airplanescheduler.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.probecat.airplanescheduler.mock.Mock
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
        // An alarm set before mock data came on mustn't act on it.
        if (Mock.active) return
        when (intent.action) {
            AlarmScheduler.CHANGE -> AirplaneController.onAlarm(context, goAsync()::finish)
            AlarmScheduler.REMIND -> ShizukuReminder.check(context, goAsync()::finish)
            // A start or end missed before boot is skipped, so schedules that ran once are over.
            Intent.ACTION_BOOT_COMPLETED -> ScheduleUpdater.expire(context, LocalDateTime.now())
            // App updates and clock changes only reset the alarms. A start or end missed in the
            // meantime is skipped.
        }
    }
}
