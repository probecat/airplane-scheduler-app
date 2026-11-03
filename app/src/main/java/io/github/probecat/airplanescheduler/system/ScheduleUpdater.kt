package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.core.Engine
import io.github.probecat.airplanescheduler.core.ScheduleEdits
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.core.ScheduleStatus
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import kotlinx.coroutines.Job

object ScheduleUpdater {
    // Saves now, so the next tap sees the new schedules. The hold ends before the refresh, so a failed start still
    // gets its notice.
    fun update(app: App, schedules: List<Schedule>): Job {
        val event = Engine.Event.Edit(app.schedules.all, schedules, LocalDateTime.now())
        app.schedules.replaceAll(schedules)
        AlarmScheduler.scheduleAll(app)
        return AirplaneController.launchHolding(app, { AirplaneController.dispatch(app, event) }) {
            Notifier.refresh(app)
        }
    }

    fun launch(app: App, result: ScheduleEdits.Result): Schedule? = when (result) {
        is ScheduleEdits.Result.Overlaps -> result.schedule
        is ScheduleEdits.Result.Saved -> {
            update(app, result.schedules)
            null
        }
    }

    // Waits for the switch, so a receiver stays alive until it ends.
    suspend fun commit(app: App, result: ScheduleEdits.Result): Schedule? = when (result) {
        is ScheduleEdits.Result.Overlaps -> result.schedule
        is ScheduleEdits.Result.Saved -> {
            update(app, result.schedules).join()
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

    // Call this only when nothing needs the end of a schedule that ran once, because a switched-off
    // schedule does not end.
    fun expire(app: App, now: LocalDateTime) {
        val schedules = ScheduleResolver.expire(app.schedules.all, now)
        if (schedules != app.schedules.all) app.schedules.replaceAll(schedules)
    }
}
