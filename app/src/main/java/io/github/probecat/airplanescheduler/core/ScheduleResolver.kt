package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.data.Schedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// Works in wall-clock time. A window starts on one of its days, or on its date when it runs once,
// and ends the next day when its end is before its start.
object ScheduleResolver {
    private const val WEEK = 7 * ScheduleTime.DAY
    private const val SKIP_KEPT_DAYS = 10L

    // How close a start is before it can be skipped or postponed, and its notification shows.
    const val AHEAD_MINUTES = 60L

    // Windows that touch act as one chain. [start] is where the chain began, [inFirst] says whether
    // [now] is still in its first window, and [disableWifi] is that first window's option.
    data class Window(val start: LocalDateTime, val inFirst: Boolean, val disableWifi: Boolean)

    // The chain that ended most recently: where it began, and its last window's schedule.
    data class Ended(val window: LocalDateTime, val schedule: Schedule)

    // A postponed window that hasn't started yet. [due] once its planned start has passed, as after a pause.
    data class Delay(val date: LocalDate, val start: LocalDateTime, val due: Boolean)

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

    // The start date of the window that is running, or else of the next one.
    fun nextDate(schedule: Schedule, now: LocalDateTime): LocalDate? {
        if (!isValid(schedule)) return null
        return runningDate(schedule, now) ?: (-1L..7L).map { now.toLocalDate().plusDays(it) }
            .firstOrNull { startsOn(schedule, it) && startOf(schedule, it).isAfter(now) }
    }

    fun nextWindow(schedule: Schedule, now: LocalDateTime): LocalDateTime? =
        nextDate(schedule, now)?.let { startOf(schedule, it) }

    fun plannedStart(schedule: Schedule, date: LocalDate): LocalDateTime =
        date.atTime(schedule.start / 60, schedule.start % 60)

    fun startOf(schedule: Schedule, date: LocalDate): LocalDateTime =
        plannedStart(schedule, date).plusMinutes(schedule.delayed[date]?.toLong() ?: 0)

    fun windowEnd(schedule: Schedule, date: LocalDate): LocalDateTime =
        plannedStart(schedule, date).plusMinutes(ScheduleTime.duration(schedule.start, schedule.end).toLong())

    // The window that starts where the one that started on [date] ends, so airplane mode stays on between them.
    fun following(schedule: Schedule, date: LocalDate, schedules: List<Schedule>): Schedule? {
        val end = windowEnd(schedule, date)
        return live(schedules).firstOrNull {
            it.id != schedule.id && startsOn(it, end.toLocalDate()) && startOf(it, end.toLocalDate()) == end
        }
    }

    fun preceding(schedule: Schedule, date: LocalDate, schedules: List<Schedule>): Schedule? {
        val start = startOf(schedule, date)
        return live(schedules).firstOrNull {
            val from = startDate(it, start.toLocalDate())
            it.id != schedule.id && startsOn(it, from) && windowEnd(it, from) == start
        }
    }

    fun pendingSkips(schedule: Schedule, now: LocalDateTime): Set<LocalDate> =
        schedule.skipped.filterTo(mutableSetOf()) { windowEnd(schedule, it).isAfter(now) }

    fun delay(schedule: Schedule, now: LocalDateTime): Delay? {
        val date = nextDate(schedule, now)?.takeIf { it in schedule.delayed } ?: return null
        val start = startOf(schedule, date).takeIf { it.isAfter(now) } ?: return null
        return Delay(date, start, due = !plannedStart(schedule, date).isAfter(now))
    }

    // Leaves out the window that is running, or else the next one. [running] when the app had started it,
    // so it ends early. Skips stay while [lastEnd] can still look back at their windows.
    fun skip(schedule: Schedule, now: LocalDateTime, running: Boolean = false): Schedule {
        val date = nextDate(schedule, now) ?: return schedule
        return schedule.copy(
            skipped = recent(schedule.skipped, now) + date,
            delayed = recent(schedule.delayed, now),
            endedEarly = recent(schedule.endedEarly, now) + setOfNotNull(date.takeIf { running }),
        )
    }

    // Moves the start of the next window [minutes] later, or pauses the running one until then. A window
    // that this leaves nothing of is skipped instead.
    fun postpone(schedule: Schedule, now: LocalDateTime, minutes: Int): Schedule {
        val date = nextDate(schedule, now) ?: return schedule
        val start = postponedStart(schedule, date, now, minutes)
        if (!start.isBefore(windowEnd(schedule, date))) return skip(schedule, now, running = isActive(schedule, now))
        val delay = Duration.between(plannedStart(schedule, date), start).toMinutes().toInt()
        return schedule.copy(delayed = recent(schedule.delayed, now) + (date to delay))
    }

    // Where [postpone] would start the window, or null when it would skip it.
    fun postponedTo(schedule: Schedule, now: LocalDateTime, minutes: Int): LocalDateTime? {
        val date = nextDate(schedule, now) ?: return null
        return postponedStart(schedule, date, now, minutes).takeIf { it.isBefore(windowEnd(schedule, date)) }
    }

    // Undoes [skip] and [postpone] for windows that haven't ended.
    fun unskip(schedule: Schedule, now: LocalDateTime): Schedule = schedule.copy(
        skipped = schedule.skipped - pendingSkips(schedule, now),
        endedEarly = schedule.endedEarly - pendingSkips(schedule, now),
        delayed = schedule.delayed.filterKeys { !windowEnd(schedule, it).isAfter(now) },
    )

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
        return Ended(chain.start, last)
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
        return (-1L..7L).firstNotNullOfOrNull { offset ->
            val date = now.toLocalDate().plusDays(offset)
            val trigger = ScheduleTime.at(startOf(schedule, date), now.zone)
            trigger.takeIf { it.isAfter(now) && startsOn(schedule, date) }
        }
    }

    fun nextEnd(schedule: Schedule, now: ZonedDateTime): ZonedDateTime? {
        if (!isValid(schedule)) return null
        return (-1L..7L).firstNotNullOfOrNull { offset ->
            val date = now.toLocalDate().plusDays(offset)
            val trigger = ScheduleTime.at(windowEnd(schedule, date), now.zone)
            trigger.takeIf { it.isAfter(now) && startsOn(schedule, date) }
        }
    }

    fun nextStart(schedules: List<Schedule>, now: ZonedDateTime): ZonedDateTime? =
        live(schedules).mapNotNull { nextStart(it, now) }.minOrNull()

    fun nextChange(schedules: List<Schedule>, now: ZonedDateTime): ZonedDateTime? =
        live(schedules).flatMap { listOfNotNull(nextStart(it, now), nextEnd(it, now)) }.minOrNull()

    fun nextNotice(schedules: List<Schedule>, now: ZonedDateTime, minutes: Long): ZonedDateTime? =
        nextStart(schedules, now.plusMinutes(minutes))?.minusMinutes(minutes)

    private fun live(schedules: List<Schedule>) = schedules.filter { it.enabled && isValid(it) }

    private fun startsOn(schedule: Schedule, date: LocalDate): Boolean =
        date !in schedule.skipped && if (schedule.once) date == schedule.date else date.dayOfWeek in schedule.days

    private fun weekdays(schedule: Schedule): Set<DayOfWeek> =
        if (schedule.once) setOf(schedule.date!!.dayOfWeek) else schedule.days

    private fun startDate(schedule: Schedule, endDate: LocalDate): LocalDate =
        if (schedule.start < schedule.end) endDate else endDate.minusDays(1)

    // A window is shorter than a day, so the one covering [now] started today or yesterday.
    private fun runningDate(schedule: Schedule, now: LocalDateTime): LocalDate? {
        if (!isValid(schedule)) return null
        return listOf(now.toLocalDate(), now.toLocalDate().minusDays(1)).firstOrNull {
            startsOn(schedule, it) && !startOf(schedule, it).isAfter(now) && windowEnd(schedule, it).isAfter(now)
        }
    }

    private fun windowStart(schedule: Schedule, now: LocalDateTime): LocalDateTime? =
        runningDate(schedule, now)?.let { startOf(schedule, it) }

    // Pausing counts from now, and postponing again from the postponed start.
    private fun postponedStart(schedule: Schedule, date: LocalDate, now: LocalDateTime, minutes: Int) =
        maxOf(startOf(schedule, date), now.truncatedTo(ChronoUnit.MINUTES)).plusMinutes(minutes.toLong())

    private fun recent(dates: Set<LocalDate>, now: LocalDateTime): Set<LocalDate> =
        dates.filterTo(mutableSetOf()) { it.isAfter(now.toLocalDate().minusDays(SKIP_KEPT_DAYS)) }

    private fun <T> recent(delays: Map<LocalDate, T>, now: LocalDateTime): Map<LocalDate, T> =
        delays.filterKeys { it.isAfter(now.toLocalDate().minusDays(SKIP_KEPT_DAYS)) }

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
            start = startOf(previous, startDate(previous, boundary.toLocalDate()))
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
