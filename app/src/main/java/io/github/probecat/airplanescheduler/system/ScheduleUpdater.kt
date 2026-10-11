package io.github.probecat.airplanescheduler.system

import android.content.Context
import io.github.probecat.airplanescheduler.core.Boundary
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

    // Only safe once nothing needs the end of a schedule that ran once, since a switched-off
    // schedule doesn't end.
    fun expire(context: Context, now: LocalDateTime) {
        val app = App.from(context)
        val schedules = ScheduleResolver.expire(app.schedules.all, now)
        if (schedules != app.schedules.all) app.schedules.replaceAll(schedules)
    }
}
