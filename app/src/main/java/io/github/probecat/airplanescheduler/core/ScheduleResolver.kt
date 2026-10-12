package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

// Works in wall-clock time. A window starts on one of its days, or on its date when it runs once,
// and ends the next day when its end is before its start.
object ScheduleResolver {
    private const val WEEK = 7 * ScheduleTime.DAY
    private const val SKIP_KEPT_DAYS = 10L

    // Windows that touch act as one chain. [start] is where the chain began, [inFirst] says whether
    // [now] is still in its first window, and [disableWifi] is that first window's option.
    data class Window(val start: LocalDateTime, val inFirst: Boolean, val disableWifi: Boolean)

    // The chain that ended most recently: where it began, and its last window's Wi-Fi option.
    data class Ended(val window: LocalDateTime, val enableWifi: Boolean)

    fun isValid(schedule: Schedule): Boolean =
        schedule.start != schedule.end && (!schedule.once || schedule.date != null)

    // A schedule that runs once keeps its date until that window ends. Otherwise it takes the first
    // window that hasn't ended, which may be running already.
    fun dated(schedule: Schedule, now: LocalDateTime): Schedule {
        if (!schedule.once) return schedule.copy(date = null)
        if (schedule.date != null && windowEnd(schedule, schedule.date).isAfter(now)) return schedule
        val date = (-1L..1L).map { now.toLocalDate().plusDays(it) }.first { windowEnd(schedule, it).isAfter(now) }
        return schedule.copy(date = date)
    }

    fun hasEnded(schedule: Schedule, now: LocalDateTime): Boolean =
        schedule.once && schedule.date != null && !windowEnd(schedule, schedule.date).isAfter(now)

    // The start of the window that is running, or else of the next one.
    fun nextWindow(schedule: Schedule, now: LocalDateTime): LocalDateTime? {
        if (!isValid(schedule)) return null
        return windowStart(schedule, now) ?: (0L..7L).firstNotNullOfOrNull { offset ->
            val date = now.toLocalDate().plusDays(offset)
            date.atTime(schedule.start / 60, schedule.start % 60).takeIf { it.isAfter(now) && startsOn(schedule, date) }
        }
    }

    // Skipped windows that haven't ended.
    fun pendingSkips(schedule: Schedule, now: LocalDateTime): Set<LocalDate> =
        schedule.skipped.filterTo(mutableSetOf()) { windowEnd(schedule, it).isAfter(now) }

    // Leaves out the window that is running, or else the next one. Skips stay while [lastEnd] can
    // still look back at their windows.
    fun skip(schedule: Schedule, now: LocalDateTime): Schedule {
        val date = nextWindow(schedule, now)?.toLocalDate() ?: return schedule
        val kept = schedule.skipped.filter { it.isAfter(now.toLocalDate().minusDays(SKIP_KEPT_DAYS)) }
        return schedule.copy(skipped = kept.toSet() + date)
    }

    fun unskip(schedule: Schedule, now: LocalDateTime): Schedule =
        schedule.copy(skipped = schedule.skipped - pendingSkips(schedule, now))

    fun expire(schedules: List<Schedule>, now: LocalDateTime): List<Schedule> =
        schedules.map { if (it.enabled && hasEnded(it, now)) it.copy(enabled = false) else it }

    fun isActive(schedule: Schedule, now: LocalDateTime): Boolean = windowStart(schedule, now) != null

    // Overlaps are refused, so at most one schedule matches.
    fun activeSchedule(schedules: List<Schedule>, now: LocalDateTime): Schedule? =
        live(schedules).firstOrNull { isActive(it, now) }

    fun window(schedules: List<Schedule>, now: LocalDateTime): Window? {
        val live = live(schedules)
        val active = live.firstOrNull { isActive(it, now) } ?: return null
        val (first, start) = firstOfChain(active, live, now) ?: return null
        return Window(start, inFirst = first.id == active.id, disableWifi = first.disableWifi)
    }

    // Asked outside a window, so the most recent end closes a chain.
    fun lastEnded(schedules: List<Schedule>, now: LocalDateTime): Ended? {
        val live = live(schedules)
        val (last, end) = lastEnd(live, now) ?: return null
        val chain = window(live, end.minusMinutes(1)) ?: return null
        return Ended(chain.start, last.enableWifi)
    }

    fun conflict(candidate: Schedule, schedules: List<Schedule>): Schedule? {
        if (!isValid(candidate)) return null
        return live(schedules).firstOrNull { it.id != candidate.id && overlaps(candidate, it) }
    }

    fun overlaps(a: Schedule, b: Schedule): Boolean {
        val aLength = ScheduleTime.duration(a.start, a.end)
        val bLength = ScheduleTime.duration(b.start, b.end)
        if (a.once && b.once) {
            val aStart = a.date!!.toEpochDay() * ScheduleTime.DAY + a.start
            val bStart = b.date!!.toEpochDay() * ScheduleTime.DAY + b.start
            return bStart - aStart in 0L until aLength || aStart - bStart in 0L until bLength
        }
        // A window that runs once overlaps a weekly one when its weekday's window would.
        return weekdays(a).any { aDay ->
            val aStart = aDay.ordinal * ScheduleTime.DAY + a.start
            weekdays(b).any { bDay ->
                val bStart = bDay.ordinal * ScheduleTime.DAY + b.start
                // Half-open windows on a circular week, so touching ends don't overlap.
                Math.floorMod(bStart - aStart, WEEK) < aLength || Math.floorMod(aStart - bStart, WEEK) < bLength
            }
        }
    }

    fun nextStart(schedule: Schedule, now: ZonedDateTime): ZonedDateTime? {
        if (!isValid(schedule)) return null
        return (0L..7L).firstNotNullOfOrNull { offset ->
            val trigger = ScheduleTime.at(now.plusDays(offset), schedule.start)
            trigger.takeIf { it.isAfter(now) && startsOn(schedule, now.toLocalDate().plusDays(offset)) }
        }
    }

    fun nextEnd(schedule: Schedule, now: ZonedDateTime): ZonedDateTime? {
        if (!isValid(schedule)) return null
        return (0L..8L).firstNotNullOfOrNull { offset ->
            val trigger = ScheduleTime.at(now.plusDays(offset), schedule.end)
            val started = startDate(schedule, now.toLocalDate().plusDays(offset))
            trigger.takeIf { it.isAfter(now) && startsOn(schedule, started) }
        }
    }

    fun nextStart(schedules: List<Schedule>, now: ZonedDateTime): ZonedDateTime? =
        live(schedules).mapNotNull { nextStart(it, now) }.minOrNull()

    fun nextChange(schedules: List<Schedule>, now: ZonedDateTime): ZonedDateTime? =
        live(schedules).flatMap { listOfNotNull(nextStart(it, now), nextEnd(it, now)) }.minOrNull()

    // [minutes] before the next start that is still at least that far away.
    fun nextReminder(schedules: List<Schedule>, now: ZonedDateTime, minutes: Long): ZonedDateTime? =
        nextStart(schedules, now.plusMinutes(minutes))?.minusMinutes(minutes)

    private fun live(schedules: List<Schedule>) = schedules.filter { it.enabled && isValid(it) }

    private fun startsOn(schedule: Schedule, date: LocalDate): Boolean =
        date !in schedule.skipped && if (schedule.once) date == schedule.date else date.dayOfWeek in schedule.days

    private fun weekdays(schedule: Schedule): Set<DayOfWeek> =
        if (schedule.once) setOf(schedule.date!!.dayOfWeek) else schedule.days

    private fun windowEnd(schedule: Schedule, date: LocalDate): LocalDateTime =
        date.atTime(schedule.start / 60, schedule.start % 60)
            .plusMinutes(ScheduleTime.duration(schedule.start, schedule.end).toLong())

    private fun startDate(schedule: Schedule, endDate: LocalDate): LocalDate =
        if (schedule.start < schedule.end) endDate else endDate.minusDays(1)

    private fun windowStart(schedule: Schedule, now: LocalDateTime): LocalDateTime? {
        if (!isValid(schedule)) return null
        val minute = now.hour * 60 + now.minute
        if (!ScheduleTime.isActive(schedule.start, schedule.end, minute)) return null
        val today = now.toLocalDate()
        val started = if (schedule.start < schedule.end || minute >= schedule.start) today else today.minusDays(1)
        return if (startsOn(schedule, started)) started.atTime(schedule.start / 60, schedule.start % 60) else null
    }

    // Walks back through windows that end exactly where the current one starts.
    private fun firstOfChain(
        active: Schedule,
        live: List<Schedule>,
        now: LocalDateTime,
    ): Pair<Schedule, LocalDateTime>? {
        var current = active
        var start = windowStart(active, now) ?: return null
        val seen = mutableSetOf(active.id)
        while (true) {
            val boundary = start
            val previous = live.firstOrNull {
                it.id !in seen && it.end == boundary.hour * 60 + boundary.minute &&
                    startsOn(it, startDate(it, boundary.toLocalDate()))
            } ?: return current to start
            seen += previous.id
            current = previous
            start = startDate(previous, boundary.toLocalDate()).atTime(previous.start / 60, previous.start % 60)
        }
    }

    private fun lastEnd(live: List<Schedule>, now: LocalDateTime): Pair<Schedule, LocalDateTime>? =
        live.mapNotNull { schedule ->
            (0L..8L).firstNotNullOfOrNull { offset ->
                val endDate = now.toLocalDate().minusDays(offset)
                val end = endDate.atTime(schedule.end / 60, schedule.end % 60)
                end.takeIf { !it.isAfter(now) && startsOn(schedule, startDate(schedule, endDate)) }
            }?.let { schedule to it }
        }.maxByOrNull { it.second }
}
