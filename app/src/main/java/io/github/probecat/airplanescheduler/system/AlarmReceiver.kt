package io.github.probecat.airplanescheduler.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
        val app = App.from(context)
        app.finishAfter(this) {
            // Notices say whether Shizuku is ready, which a fresh process learns only after a moment.
            app.switcher.connect()
            app.refreshShizuku()
            when (intent.action) {
                AlarmScheduler.CHANGE -> AirplaneController.onAlarm(app)
                AlarmScheduler.NOTICE -> {}
                // Boot, app updates and clock changes reset the alarms and notices. A start or end missed
                // in the meantime is skipped, so schedules that ran once are over.
                else -> {
                    AirplaneController.forgetMissedEnd(app)
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED) ScheduleUpdater.expire(app, LocalDateTime.now())
                }
            }
            Notifier.refresh(app)
        }
    }
}
