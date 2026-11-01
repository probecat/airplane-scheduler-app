package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

// What each edit makes of [all] the schedules, or the schedule it would overlap instead.
object ScheduleEdits {
    sealed interface Result {
        data class Saved(val schedules: List<Schedule>) : Result

        data class Overlaps(val schedule: Schedule) : Result
    }

    // Only enabled schedules are checked, so a disabled one may overlap anything, and so may one
    // that ran once and is over.
    fun conflict(all: List<Schedule>, schedule: Schedule, now: LocalDateTime): Schedule? {
        if (!schedule.enabled) return null
        val others = all.filterNot { ScheduleResolver.hasEnded(it, now) }
        return ScheduleResolver.conflict(ScheduleResolver.dated(schedule, now), others)
    }

    // New times drop the delays, which were for the old ones.
    fun save(all: List<Schedule>, edited: Schedule, now: LocalDateTime): Result {
        val old = all.find { it.id == edited.id }
        val retimed = old != null && (old.start != edited.start || old.end != edited.end)
        val schedule = ScheduleResolver.dated(if (retimed) edited.copy(delayed = emptyMap()) else edited, now)
        conflict(all, schedule, now)?.let { return Result.Overlaps(it) }
        return Result.Saved(if (old != null) all.map { if (it.id == schedule.id) schedule else it } else all + schedule)
    }

    // The sheet doesn't edit whether a schedule is on, nor its skips and delays, which a notification may
    // have changed while it was open.
    fun saveSheet(all: List<Schedule>, edited: Schedule, now: LocalDateTime): Result {
        val stored = all.find { it.id == edited.id }
        val schedule = stored?.let {
            edited.copy(enabled = it.enabled, skipped = it.skipped, endedEarly = it.endedEarly, delayed = it.delayed)
        } ?: edited
        return save(all, schedule, now)
    }

    fun setEnabled(all: List<Schedule>, schedule: Schedule, enabled: Boolean, now: LocalDateTime): Result {
        val base = if (enabled) ScheduleResolver.unskip(schedule, now) else schedule
        return save(all, base.copy(enabled = enabled), now)
    }

    // Skips the next window, or ends the running one early. A schedule that runs once is switched off
    // instead, since that is its only window. A running window whose start was [missed] is skipped, since
    // the app never started it.
    fun skip(all: List<Schedule>, schedule: Schedule, now: LocalDateTime, missed: Boolean): Result {
        if (schedule.once) return setEnabled(all, schedule, false, now)
        val running = ScheduleResolver.isActive(schedule, now) && !missed
        return save(all, ScheduleResolver.skip(schedule, now, running), now)
    }

    fun postpone(all: List<Schedule>, schedule: Schedule, now: LocalDateTime, minutes: Int): Result =
        save(all, ScheduleResolver.postpone(schedule, now, minutes), now)

    // Undoes [skip] and [postpone], which starts the window again if it's still running.
    fun unskip(all: List<Schedule>, schedule: Schedule, now: LocalDateTime): Result =
        if (schedule.once && !schedule.enabled) {
            setEnabled(all, schedule, true, now)
        } else {
            save(all, ScheduleResolver.unskip(schedule, now), now)
        }

    fun delete(all: List<Schedule>, id: Long): Result = Result.Saved(all.filterNot { it.id == id })

    // Undoes a delete. It takes [newId] if its old id was reused, and comes back switched off if it now
    // overlaps another schedule.
    fun restore(all: List<Schedule>, schedule: Schedule, newId: Long, now: LocalDateTime): Result {
        val id = if (all.none { it.id == schedule.id }) schedule.id else newId
        val restored = schedule.copy(id = id)
        val overlaps = conflict(all, restored, now) != null
        return Result.Saved(all + if (overlaps) restored.copy(enabled = false) else restored)
    }
}
