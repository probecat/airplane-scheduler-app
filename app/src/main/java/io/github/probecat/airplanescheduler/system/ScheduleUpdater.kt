package io.github.probecat.airplanescheduler.system

import android.content.Context
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.mock.Mock
import java.time.LocalDateTime

object ScheduleUpdater {
    fun update(context: Context, schedules: List<Schedule>, done: () -> Unit = {}) {
        val app = App.from(context)
        if (Mock.active) {
            app.schedules.replaceAll(schedules)
            Notifier.refresh(app)
            done()
            return
        }
        val step = Boundary.atEdit(app.schedules.all, schedules, LocalDateTime.now(), app.settings.started)
        // Released before the refresh, so a start that failed still gets its notice.
        AirplaneController.hold { release ->
            app.schedules.replaceAll(schedules)
            AlarmScheduler.scheduleAll(app)
            AirplaneController.run(app, step) {
                release()
                Notifier.refresh(app)
                done()
            }
        }
    }

    // Only enabled schedules are checked, so a disabled one may overlap anything, and so may one
    // that ran once and is over.
    fun conflict(context: Context, schedule: Schedule): Schedule? {
        if (!schedule.enabled) return null
        val now = Mock.now()
        val others = App.from(context).schedules.all.filterNot { ScheduleResolver.hasEnded(it, now) }
        return ScheduleResolver.conflict(ScheduleResolver.dated(schedule, now), others)
    }

    // Returns the overlapping schedule instead of saving. New times drop the delays, which were for the
    // old ones.
    fun save(context: Context, edited: Schedule, done: () -> Unit = {}): Schedule? {
        val app = App.from(context)
        val old = app.schedules.get(edited.id)
        val retimed = old != null && (old.start != edited.start || old.end != edited.end)
        val schedule = ScheduleResolver.dated(if (retimed) edited.copy(delayed = emptyMap()) else edited, Mock.now())
        conflict(app, schedule)?.let {
            done()
            return it
        }
        val all = app.schedules.all
        val replaced = all.map { if (it.id == schedule.id) schedule else it }
        update(app, if (old != null) replaced else all + schedule, done)
        return null
    }

    fun setEnabled(context: Context, schedule: Schedule, enabled: Boolean, done: () -> Unit = {}): Schedule? {
        val base = if (enabled) ScheduleResolver.unskip(schedule, Mock.now()) else schedule
        return save(context, base.copy(enabled = enabled), done)
    }

    // Skips the next window, or ends the running one early. A schedule that runs once is switched off
    // instead, since that is its only window. A running window whose start was missed is skipped, since
    // the app never started it.
    fun skip(context: Context, schedule: Schedule, done: () -> Unit = {}) {
        val app = App.from(context)
        if (schedule.once) {
            setEnabled(app, schedule, false, done)
            return
        }
        val now = Mock.now()
        val missed = Boundary.missed(
            app.schedules.all,
            now,
            app.settings.started,
            AirplaneController.airplaneOn(app) && !Mock.active,
            AirplaneController.switching,
        ) != null
        val running = ScheduleResolver.isActive(schedule, now) && !missed
        save(app, ScheduleResolver.skip(schedule, now, running), done)
    }

    fun postpone(context: Context, schedule: Schedule, minutes: Int, done: () -> Unit = {}) {
        save(context, ScheduleResolver.postpone(schedule, Mock.now(), minutes), done)
    }

    // Undoes [skip] and [postpone], which starts the window again if it's still running.
    fun unskip(context: Context, id: Long, done: () -> Unit = {}): Schedule? {
        val schedule = App.from(context).schedules.get(id) ?: return null.also { done() }
        return if (schedule.once && !schedule.enabled) {
            setEnabled(context, schedule, true, done)
        } else {
            save(context, ScheduleResolver.unskip(schedule, Mock.now()), done)
        }
    }

    // Starts the running window that the app missed and reports whether airplane mode came on.
    fun catchUp(context: Context, done: (Boolean) -> Unit) {
        val app = App.from(context)
        val window = ScheduleResolver.window(app.schedules.all, Mock.now()) ?: return done(false)
        if (Mock.active) {
            val ready = ShizukuStatus.current() == ShizukuState.READY
            if (ready) app.settings.started = Boundary.Started(window.start, Changes(true, window.disableWifi))
            Notifier.refresh(app)
            done(ready)
            return
        }
        AirplaneController.run(app, Boundary.Step.Start(window)) {
            Notifier.refresh(app)
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
