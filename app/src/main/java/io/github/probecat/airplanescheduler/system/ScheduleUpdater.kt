package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.core.Engine
import io.github.probecat.airplanescheduler.core.ScheduleEdits
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.core.ScheduleStatus
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

object ScheduleUpdater {
    // The hold ends before the refresh, so a failed start still gets its notice.
    suspend fun update(app: App, schedules: List<Schedule>) {
        AirplaneController.holding {
            val event = Engine.Event.Edit(app.schedules.all, schedules, LocalDateTime.now())
            app.schedules.replaceAll(schedules)
            AlarmScheduler.scheduleAll(app)
            AirplaneController.dispatch(app, event)
        }
        Notifier.refresh(app)
    }

    suspend fun commit(app: App, result: ScheduleEdits.Result): Schedule? = when (result) {
        is ScheduleEdits.Result.Overlaps -> result.schedule
        is ScheduleEdits.Result.Saved -> {
            update(app, result.schedules)
            null
        }
    }

    // Runs in the app scope, so it continues after the screen closes.
    fun launch(app: App, result: ScheduleEdits.Result): Schedule? = when (result) {
        is ScheduleEdits.Result.Overlaps -> result.schedule
        is ScheduleEdits.Result.Saved -> {
            app.launch { update(app, result.schedules) }
            null
        }
    }

    fun status(app: App, schedule: Schedule, now: LocalDateTime): ScheduleStatus? = ScheduleStatus.of(
        schedule,
        app.schedules.all,
        now,
        schedule.id == AirplaneController.missedSchedule(app, now)?.id,
    )

    // Starts the running window that the app missed and reports whether airplane mode came on.
    suspend fun catchUp(app: App): Boolean {
        AirplaneController.dispatch(app, Engine.Event.CatchUp(app.schedules.all, LocalDateTime.now()))
        Notifier.refresh(app)
        return AirplaneController.airplaneOn(app)
    }

    // Only safe once nothing needs the end of a schedule that ran once, since a switched-off
    // schedule doesn't end.
    fun expire(app: App, now: LocalDateTime) {
        val schedules = ScheduleResolver.expire(app.schedules.all, now)
        if (schedules != app.schedules.all) app.schedules.replaceAll(schedules)
    }
}
