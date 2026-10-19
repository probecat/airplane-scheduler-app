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
        val app = App.from(context)
        val result = goAsync()
        // Notices say whether Shizuku is ready, which a fresh process learns only after a moment.
        AirplaneController.awaitBinder {
            app.refreshShizuku()
            val done = {
                Notifier.refresh(app)
                result.finish()
            }
            when (intent.action) {
                AlarmScheduler.CHANGE -> AirplaneController.onAlarm(app, done)
                AlarmScheduler.NOTICE -> done()
                // Boot, app updates and clock changes reset the alarms and notices. A start or end missed
                // in the meantime is skipped, so schedules that ran once are over.
                else -> {
                    AirplaneController.forgetMissedEnd(app)
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED) ScheduleUpdater.expire(app, LocalDateTime.now())
                    done()
                }
            }
        }
    }
}
