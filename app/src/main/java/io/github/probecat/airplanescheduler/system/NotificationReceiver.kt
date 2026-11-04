package io.github.probecat.airplanescheduler.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleEdits
import io.github.probecat.airplanescheduler.core.ScheduleStatus
import io.github.probecat.airplanescheduler.core.ScheduleStatus.Action
import java.time.LocalDate
import java.time.LocalDateTime

// Each action acts only on the window and state that its notification was about. A tap on an old notification only
// updates the notification.
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = App.from(context)
        val id = intent.getLongExtra(Notifier.ID, 0)
        if (intent.action == Notifier.DISMISSED) {
            intent.getStringExtra(Notifier.KEY)?.let { Notifier.dismissed(app, id, it) }
            return
        }
        app.finishAfter(this) {
            app.switcher.connect(tap = true)
            if (intent.action == Notifier.RETRY) {
                val report = Notifier.Report(id, intent.getStringExtra(Notifier.TITLE).orEmpty())
                AirplaneController.undo(app, Changes.of(intent.getIntExtra(Notifier.CHANGES, 0)), report)
                return@finishAfter
            }
            app.refreshAccess()
            act(app, id, intent)
            Notifier.refresh(app)
        }
    }

    private suspend fun act(app: App, id: Long, intent: Intent) {
        val schedule = app.schedules.get(id) ?: return
        val date = intent.getStringExtra(Notifier.DATE)?.let(LocalDate::parse) ?: return
        val now = LocalDateTime.now()
        val status = ScheduleUpdater.status(app, schedule, now)
        val all = app.schedules.all
        when (intent.action) {
            // After Skip or End, the only action left is Undo, and only the app has Undo.
            Notifier.SKIP, Notifier.END -> if (status?.allows(Action.SKIP, date) == true) {
                val missed = (status as? ScheduleStatus.Running)?.missed == true
                ScheduleUpdater.commit(app, ScheduleEdits.skip(all, schedule, now, missed))
                Notifier.dismiss(app, id)
            }
            Notifier.POSTPONE -> if (status?.allows(Action.POSTPONE, date) == true) {
                ScheduleUpdater.commit(
                    app,
                    ScheduleEdits.postpone(all, schedule, now, intent.getIntExtra(Notifier.MINUTES, 0)),
                )
            }
            Notifier.UNDO -> if (status?.allows(Action.UNDO, date) == true) {
                ScheduleUpdater.commit(app, ScheduleEdits.unskip(all, schedule, now))
            }
            Notifier.TURN_ON -> if (status?.allows(Action.TURN_ON, date) == true) ScheduleUpdater.catchUp(app)
        }
    }
}
