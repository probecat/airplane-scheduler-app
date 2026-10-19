package io.github.probecat.airplanescheduler.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.mock.Mock
import java.time.LocalDate

// Notification actions. Each acts only on the window its notification was about, so a tap on one that
// is out of date just brings it up to date.
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = App.from(context)
        val id = intent.getLongExtra(Notifier.ID, 0)
        if (intent.action == Notifier.DISMISSED) {
            intent.getStringExtra(Notifier.KEY)?.let { Notifier.dismissed(app, id, it) }
            return
        }
        val result = goAsync()
        val done = {
            Notifier.refresh(app)
            result.finish()
        }
        if (intent.action == Notifier.RETRY) {
            val report = Notifier.Report(id, intent.getStringExtra(Notifier.TITLE).orEmpty())
            val changes = Changes.of(intent.getIntExtra(Notifier.CHANGES, 0))
            AirplaneController.awaitBinder(tap = true) { AirplaneController.undo(app, changes, report, result::finish) }
            return
        }
        val schedule = app.schedules.get(id)
        val date = intent.getStringExtra(Notifier.DATE)?.let(LocalDate::parse)
        if (schedule == null || date == null) return done()
        val now = Mock.now()
        AirplaneController.awaitBinder(tap = true) {
            app.refreshShizuku()
            when (intent.action) {
                Notifier.SKIP ->
                    if (ScheduleResolver.nextDate(schedule, now) == date) {
                        ScheduleUpdater.skip(app, schedule, done)
                    } else {
                        done()
                    }
                // Ending leaves nothing to act on but Undo, which the app has too.
                Notifier.END ->
                    if (ScheduleResolver.nextDate(schedule, now) == date) {
                        ScheduleUpdater.skip(app, schedule) {
                            Notifier.dismiss(app, id)
                            done()
                        }
                    } else {
                        done()
                    }
                Notifier.POSTPONE ->
                    if (ScheduleResolver.nextDate(schedule, now) == date) {
                        ScheduleUpdater.postpone(app, schedule, intent.getIntExtra(Notifier.MINUTES, 0), done)
                    } else {
                        done()
                    }
                Notifier.UNDO -> {
                    val pending = date in ScheduleResolver.pendingSkips(schedule, now) ||
                        ScheduleResolver.delay(schedule, now)?.date == date
                    if (pending) ScheduleUpdater.unskip(app, id, done) else done()
                }
                Notifier.TURN_ON -> ScheduleUpdater.catchUp(app) { done() }
                else -> done()
            }
        }
    }
}
