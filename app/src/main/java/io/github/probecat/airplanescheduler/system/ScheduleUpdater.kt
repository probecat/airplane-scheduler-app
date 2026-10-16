package io.github.probecat.airplanescheduler.system

import android.content.Context
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.mock.Mock
import java.time.LocalDateTime

object ScheduleUpdater {
    fun update(context: Context, schedules: List<Schedule>) {
        val app = App.from(context)
        if (Mock.active) {
            app.schedules.replaceAll(schedules)
            return
        }
        val step = Boundary.atEdit(app.schedules.all, schedules, LocalDateTime.now(), app.settings.started)
        app.schedules.replaceAll(schedules)
        AlarmScheduler.scheduleAll(app)
        AirplaneController.run(app, step) {}
    }

    // Starts the running window that the app missed and reports whether airplane mode came on.
    fun catchUp(context: Context, done: (Boolean) -> Unit) {
        val app = App.from(context)
        val window = ScheduleResolver.window(app.schedules.all, Mock.now()) ?: return done(false)
        if (Mock.active) {
            val ready = ShizukuStatus.current() == ShizukuState.READY
            if (ready) app.settings.started = Boundary.Started(window.start, Changes(true, window.disableWifi))
            done(ready)
            return
        }
        AirplaneController.run(app, Boundary.Step.Start(window)) {
            done(AirplaneController.airplaneOn(app))
        }
    }

    // Only safe once nothing needs the end of a schedule that ran once, since a switched-off
    // schedule doesn't end.
    fun expire(context: Context, now: LocalDateTime) {
        val app = App.from(context)
        val schedules = ScheduleResolver.expire(app.schedules.all, now)
        if (schedules != app.schedules.all) app.schedules.replaceAll(schedules)
    }
}
