package io.github.probecat.airplanescheduler.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        // Read the time before the Shizuku wait. Otherwise an end that arrives during the wait counts as missed.
        val now = LocalDateTime.now()
        val zone = if (action == Intent.ACTION_TIMEZONE_CHANGED) " (${ZoneId.systemDefault()})" else ""
        App.from(context).log.add(Events.received(action) + zone)
        AlarmScheduler.scheduleAll(context)
        val app = App.from(context)
        app.finishAfter(this) {
            // Notices tell whether Shizuku is ready. A new process learns this only after a short time.
            app.switcher.connect()
            app.refreshAccess()
            when (action) {
                AlarmScheduler.CHANGE -> AirplaneController.onAlarm(app, now)
                AlarmScheduler.NOTICE -> {}
                // Boot, app updates and clock changes reset the alarms and notices. A start or end missed
                // in the meantime is skipped, so schedules that ran once are over.
                else -> {
                    AirplaneController.forgetMissedEnd(app, now)
                    if (action == Intent.ACTION_BOOT_COMPLETED) ScheduleUpdater.expire(app, now)
                }
            }
            Notifier.refresh(app)
        }
    }
}
