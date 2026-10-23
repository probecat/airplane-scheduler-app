package io.github.probecat.airplanescheduler.ui

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.mock.Mock
import io.github.probecat.airplanescheduler.mock.activeMock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// String resources, behind an interface so unit tests can read them without Android.
interface Strings {
    fun get(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

private class ResourceStrings(private val resources: Resources) : Strings {
    override fun get(id: Int, vararg args: Any): String = resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String = resources.getQuantityString(id, count, *args)
}

class Format(val is24Hour: Boolean, val locale: Locale, private val strings: Strings) {
    private val clock = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm", locale)
    private val marker = DateTimeFormatter.ofPattern("a", locale)

    val week: List<DayOfWeek> = WeekFields.of(locale).firstDayOfWeek.let { first ->
        List(7) { first.plus(it.toLong()) }
    }

    fun time(minute: Int): String = time(minute, markerSize = TextUnit.Unspecified).text

    fun time(time: LocalDateTime): String = time(time.hour * 60 + time.minute)

    fun time(minute: Int, markerSize: TextUnit): AnnotatedString {
        val time = LocalTime.of(minute / 60, minute % 60)
        return buildAnnotatedString {
            append(clock.format(time))
            if (!is24Hour) {
                withStyle(SpanStyle(fontSize = markerSize)) { append("$NBSP${marker.format(time)}") }
            }
        }
    }

    fun title(schedule: Schedule): String = schedule.name.ifBlank { window(schedule) }

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
        if (days == Schedule.EVERY_DAY) return strings.get(R.string.days_every_day)
        val ordered = week.filter { it in days }
        if (ordered.isEmpty()) return strings.get(R.string.days_once)
        val run = ordered.size >= 3 && week.indexOf(ordered.last()) - week.indexOf(ordered.first()) == ordered.size - 1
        if (run) return "${short(ordered.first())}–${short(ordered.last())}"
        return ordered.joinToString(" ", transform = ::short)
    }

    fun repeats(schedule: Schedule, today: LocalDate): String =
        if (schedule.once) onceDate(schedule, today).replaceFirstChar { it.titlecase(locale) } else days(schedule.days)

    // Nothing is scheduled while it's off, since switching on picks a new date.
    private fun onceDate(schedule: Schedule, today: LocalDate): String {
        val start = schedule.date?.takeIf { schedule.enabled } ?: return strings.get(R.string.once_not_scheduled)
        return when (start) {
            today -> strings.get(R.string.day_today)
            today.plusDays(1) -> strings.get(R.string.day_tomorrow)
            today.minusDays(1) -> strings.get(R.string.day_yesterday)
            else -> start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }

    // A weekday only within the coming week, since a week ahead would read as today.
    fun upcomingDay(date: LocalDate, today: LocalDate): String = when {
        date == today -> strings.get(R.string.day_today)
        date == today.plusDays(1) -> strings.get(R.string.day_tomorrow)
        date.isBefore(today.plusDays(7)) -> dayName(date.dayOfWeek)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }

    // "Skipped, resumes tomorrow" for the window that started on [date].
    fun skipped(schedule: Schedule, date: LocalDate, now: LocalDateTime): String {
        val started = !ScheduleResolver.plannedStart(schedule, date).isAfter(now)
        val next = ScheduleResolver.nextWindow(schedule, now)
            ?: return strings.get(if (started) R.string.ended_early else R.string.skipped)
        val day = upcomingDay(next.toLocalDate(), now.toLocalDate())
        return strings.get(if (started) R.string.ended_early_resumes else R.string.skipped_resumes, day)
    }

    fun delayed(start: LocalDateTime): String = strings.get(R.string.postponed_to, time(start))

    fun sentences(first: String, second: String): String = strings.get(R.string.sentences, first, second)

    private fun short(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale)

    fun dayLetter(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)

    fun label(schedule: Schedule, today: LocalDate): String {
        if (schedule.name.isNotBlank()) return strings.get(R.string.schedule_label_named, schedule.name)
        val days = when {
            schedule.once -> onceDate(schedule, today)
            schedule.days == Schedule.EVERY_DAY -> strings.get(R.string.schedule_label_every_day)
            else -> days(schedule.days)
        }
        return strings.get(R.string.schedule_label_unnamed, window(schedule), days)
    }

    // Minutes are left out once it's a day or more.
    fun duration(minutes: Int): String {
        val days = minutes / DAY
        val hours = minutes % DAY / 60
        val rest = minutes % 60
        fun days() = strings.plural(R.plurals.duration_days, days, days)
        fun hours() = strings.plural(R.plurals.duration_hours, hours, hours)
        fun minutes() = strings.plural(R.plurals.duration_minutes, rest, rest)
        return when {
            days > 0 && hours == 0 -> days()
            days > 0 -> strings.get(R.string.duration_pair, days(), hours())
            hours == 0 -> minutes()
            rest == 0 -> hours()
            else -> strings.get(R.string.duration_pair, hours(), minutes())
        }
    }

    companion object {
        private const val DAY = 24 * 60

        val SMALL = 0.5.em
        val MARKER = 0.35.em

        // Keeps "11:00 PM" on one line.
        private const val NBSP = '\u00A0'
    }
}

fun format(context: Context): Format = Format(
    (if (Mock.active) Mock.config.value.is24Hour else null) ?: DateFormat.is24HourFormat(context),
    (if (Mock.active) Mock.config.value.locale?.let(Locale::forLanguageTag) else null)
        ?: context.resources.configuration.locales[0],
    ResourceStrings(context.resources),
)

@Composable
fun rememberFormat(): Format {
    // Reading the configuration recomposes when the locale changes.
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = activeMock()?.is24Hour ?: DateFormat.is24HourFormat(LocalContext.current)
    val resources = LocalResources.current
    return remember(is24Hour, locale, resources) { Format(is24Hour, locale, ResourceStrings(resources)) }
}
