package io.github.probecat.airplanescheduler.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AlarmScheduler.scheduleAll(context)
        val app = App.from(context)
        app.finishAfter(this) {
            // Notices tell whether Shizuku is ready. A new process learns this only after a short time.
            app.switcher.connect()
            app.refreshShizuku()
            when (action) {
                AlarmScheduler.CHANGE -> AirplaneController.onAlarm(app)
                AlarmScheduler.NOTICE -> {}
                // Boot, app updates and clock changes reset the alarms and notices. A start or end missed
                // in the meantime is skipped, so schedules that ran once are over.
                else -> {
                    AirplaneController.forgetMissedEnd(app)
                    if (action == Intent.ACTION_BOOT_COMPLETED) ScheduleUpdater.expire(app, LocalDateTime.now())
                }
            }
            Notifier.refresh(app)
        }
    }
}
