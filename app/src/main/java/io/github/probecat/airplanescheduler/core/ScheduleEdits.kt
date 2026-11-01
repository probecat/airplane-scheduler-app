package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime

object ScheduleEdits {
    sealed interface Result {
        data class Saved(val schedules: List<Schedule>) : Result

        data class Overlaps(val schedule: Schedule) : Result
    }

    // Only enabled schedules count. A disabled schedule can overlap any schedule, and so can a schedule that ran once
    // and is over.
    fun conflict(all: List<Schedule>, schedule: Schedule, now: LocalDateTime): Schedule? {
        if (!schedule.enabled) return null
        val others = all.filterNot { ScheduleResolver.hasEnded(it, now) }
        return ScheduleResolver.conflict(ScheduleResolver.dated(schedule, now), others)
    }

    // New times remove the delays, because the delays were for the old times.
    fun save(all: List<Schedule>, edited: Schedule, now: LocalDateTime): Result {
        val old = all.find { it.id == edited.id }
        val retimed = old != null && (old.start != edited.start || old.end != edited.end)
        val schedule = ScheduleResolver.dated(if (retimed) edited.copy(delayed = emptyMap()) else edited, now)
        conflict(all, schedule, now)?.let { return Result.Overlaps(it) }
        return Result.Saved(if (old != null) all.map { if (it.id == schedule.id) schedule else it } else all + schedule)
    }

    // The sheet does not edit whether a schedule is on, or its skips and delays. A notification can change those while
    // the sheet is open.
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

    // Skips the next window, or ends the running window early. A schedule that runs once is switched off instead,
    // because that window is its only one. A running window with a [missed] start is skipped, because the app never
    // started it.
    fun skip(all: List<Schedule>, schedule: Schedule, now: LocalDateTime, missed: Boolean): Result {
        if (schedule.once) return setEnabled(all, schedule, false, now)
        val running = ScheduleResolver.isActive(schedule, now) && !missed
        return save(all, ScheduleResolver.skip(schedule, now, running), now)
    }

    fun postpone(all: List<Schedule>, schedule: Schedule, now: LocalDateTime, minutes: Int): Result =
        save(all, ScheduleResolver.postpone(schedule, now, minutes), now)

    fun unskip(all: List<Schedule>, schedule: Schedule, now: LocalDateTime): Result =
        if (schedule.once && !schedule.enabled) {
            setEnabled(all, schedule, true, now)
        } else {
            save(all, ScheduleResolver.unskip(schedule, now), now)
        }

    fun delete(all: List<Schedule>, id: Long): Result = Result.Saved(all.filterNot { it.id == id })

    // Undoes a delete. The schedule takes [newId] if another schedule now uses its old id. It comes back switched off
    // if it now overlaps another schedule.
    fun restore(all: List<Schedule>, schedule: Schedule, newId: Long, now: LocalDateTime): Result {
        val id = if (all.none { it.id == schedule.id }) schedule.id else newId
        val restored = schedule.copy(id = id)
        val overlaps = conflict(all, restored, now) != null
        return Result.Saved(all + if (overlaps) restored.copy(enabled = false) else restored)
    }
}
