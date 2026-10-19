package io.github.probecat.airplanescheduler.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.mock.activeMock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

class Format(val is24Hour: Boolean, val locale: Locale) {
    private val clock = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale)
    private val marker = DateTimeFormatter.ofPattern("a", locale)

    val week: List<DayOfWeek> = WeekFields.of(locale).firstDayOfWeek.let { first ->
        List(7) { first.plus(it.toLong()) }
    }

    fun time(minute: Int): String = time(minute, markerSize = TextUnit.Unspecified).text

    fun time(minute: Int, markerSize: TextUnit): AnnotatedString {
        val time = LocalTime.of(minute / 60, minute % 60)
        return buildAnnotatedString {
            append(clock.format(time))
            if (!is24Hour) {
                withStyle(SpanStyle(fontSize = markerSize)) { append("$NBSP${marker.format(time)}") }
            }
        }
    }

    fun window(schedule: Schedule): String = "${time(schedule.start)} – ${time(schedule.end)}"

    fun window(schedule: Schedule, markerSize: TextUnit): AnnotatedString = buildAnnotatedString {
        append(time(schedule.start, markerSize))
        append(" ")
        // Raised to sit level with the middle of the digits.
        withStyle(SpanStyle(fontSize = SMALL, baselineShift = BaselineShift(0.2f))) { append("–") }
        append(" ")
        append(time(schedule.end, markerSize))
    }

    // A single run of three or more days as a range, otherwise each day.
    fun days(days: Set<DayOfWeek>): String {
        if (days == Schedule.EVERY_DAY) return "Every day"
        val ordered = week.filter { it in days }
        if (ordered.isEmpty()) return "Once"
        val run = ordered.size >= 3 && week.indexOf(ordered.last()) - week.indexOf(ordered.first()) == ordered.size - 1
        if (run) return "${short(ordered.first())}–${short(ordered.last())}"
        return ordered.joinToString(" ", transform = ::short)
    }

    fun repeats(schedule: Schedule, today: LocalDate): String =
        if (schedule.once) onceDate(schedule, today).replaceFirstChar { it.titlecase(locale) } else days(schedule.days)

    // Nothing is scheduled while it's off, since switching on picks a new date.
    private fun onceDate(schedule: Schedule, today: LocalDate): String {
        val start = schedule.date?.takeIf { schedule.enabled } ?: return "not scheduled"
        return when (start) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            today.minusDays(1) -> "yesterday"
            else -> start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }

    // A weekday only within the coming week, since a week ahead would read as today.
    fun upcomingDay(date: LocalDate, today: LocalDate): String = when {
        date == today -> "today"
        date == today.plusDays(1) -> "tomorrow"
        date.isBefore(today.plusDays(7)) -> dayName(date.dayOfWeek)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }

    private fun short(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale)

    fun dayLetter(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)

    fun label(schedule: Schedule, today: LocalDate): String {
        if (schedule.name.isNotBlank()) return "“${schedule.name}”"
        val days = when {
            schedule.once -> onceDate(schedule, today)
            schedule.days == Schedule.EVERY_DAY -> "every day"
            else -> days(schedule.days)
        }
        return "${window(schedule)} ($days)"
    }

    // Minutes are left out once it's a day or more.
    fun duration(minutes: Int): String {
        val days = minutes / DAY
        val hours = minutes % DAY / 60
        val rest = minutes % 60
        return when {
            days > 0 && hours == 0 -> "$days${NBSP}d"
            days > 0 -> "$days${NBSP}d $hours${NBSP}h"
            hours == 0 -> "$rest${NBSP}min"
            rest == 0 -> "$hours${NBSP}h"
            else -> "$hours${NBSP}h $rest${NBSP}min"
        }
    }

    companion object {
        private const val DAY = 24 * 60

        val SMALL = 0.5.em
        val MARKER = 0.35.em

        // Keeps "11:00 PM" and "8 h" on one line.
        private const val NBSP = '\u00A0'
    }
}

@Composable
fun rememberFormat(): Format {
    // Reading the configuration recomposes when the locale changes.
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = activeMock()?.is24Hour ?: DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour, locale) { Format(is24Hour, locale) }
}
