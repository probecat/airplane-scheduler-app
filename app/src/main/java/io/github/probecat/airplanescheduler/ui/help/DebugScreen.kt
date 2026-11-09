package io.github.probecat.airplanescheduler.ui.help

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.BuildConfig
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.core.ScheduleTime
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.data.Settings
import io.github.probecat.airplanescheduler.system.Access
import io.github.probecat.airplanescheduler.system.AirplaneController
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.Events
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.exactAlarmsAllowed
import io.github.probecat.airplanescheduler.system.exactAlarmsDeniable
import io.github.probecat.airplanescheduler.system.notificationsAllowed
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.theme.hasDynamicColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun DebugScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = App.from(context)
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val airplane by app.airplane.collectAsStateWithLifecycle()
    val switching by AirplaneController.inFlightCount.collectAsStateWithLifecycle()
    val events by app.log.entries.collectAsStateWithLifecycle()
    val sections = sections(context, schedules, settings, access, now, airplane, switching > 0) +
        Section("Recent events", lines = events.reversed().ifEmpty { listOf("none") })
    Subpage(
        title = "Debug info",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Airplane Scheduler debug info", text(sections)))
            }) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = "Copy")
            }
        },
    ) {
        SelectionContainer {
            Column(Modifier.padding(bottom = 24.dp)) {
                sections.forEach { SectionView(it) }
            }
        }
    }
}

private class Section(
    val title: String,
    val rows: List<Pair<String, String>> = emptyList(),
    val lines: List<String> = emptyList(),
)

@Composable
private fun SectionView(section: Section) {
    Text(
        section.title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, value) in section.rows) {
            Row {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(152.dp),
                )
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
    // Lines scroll sideways instead of wrapping, so that each takes one row on small screens.
    if (section.lines.isNotEmpty()) {
        Column(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (line in section.lines) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                )
            }
        }
    }
}

private fun text(sections: List<Section>): String = sections.joinToString("\n\n") { section ->
    (listOf("## ${section.title}") + section.rows.map { (label, value) -> "$label: $value" } + section.lines)
        .joinToString("\n")
}

private val moment = Events.MOMENT

private fun sections(
    context: Context,
    schedules: List<Schedule>,
    settings: Settings,
    access: Access,
    now: LocalDateTime,
    airplane: Boolean,
    switching: Boolean,
): List<Section> {
    val app = App.from(context)
    val zoned = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS)
    val active = ScheduleResolver.activeSchedule(schedules, now)
    val started = app.engine.state.started
    val change = ScheduleResolver.nextChange(schedules, zoned)
    val notice = ScheduleResolver.nextNotice(schedules, zoned, ScheduleResolver.AHEAD_MINUTES)
    val blocked = NotificationManagerCompat.from(context).notificationChannelsCompat
        .filter { it.importance == NotificationManagerCompat.IMPORTANCE_NONE }
        .joinToString(" | ") { it.name.toString() }
    val ignoresBattery = context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
    fun allowed(yes: Boolean) = if (yes) "ALLOWED" else "DENIED"
    return listOf(
        Section(
            "App and phone",
            listOf(
                "Version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "Package" to context.packageName,
                "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "Time" to DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zoned),
                "Time zone" to zoned.zone.id,
            ),
        ),
        Section(
            "Access",
            listOfNotNull(
                "Method" to settings.backend.name,
                "Access" to Events.access(access),
                ("Shizuku app" to if (ShizukuStatus.launchIntent(context) != null) "INSTALLED" else "NOT_INSTALLED")
                    .takeIf { access.kind == Access.Kind.SHIZUKU },
                "Switching" to Events.yesNo(switching),
                "Airplane mode" to Events.onOff(airplane),
                "Wi-Fi" to Events.onOff(AirplaneController.wifiOn(context)),
            ),
        ),
        Section(
            "Alarms and notifications",
            listOfNotNull(
                "Next alarm" to (change?.let(moment::format) ?: "none"),
                "Next notice" to (notice?.let(moment::format) ?: "none"),
                ("Exact alarms" to allowed(exactAlarmsAllowed(context))).takeIf { exactAlarmsDeniable },
                "Battery optimization" to Events.onOff(!ignoresBattery),
                "Notifications" to allowed(notificationsAllowed(context)),
                "Blocked channels" to blocked.ifEmpty { "none" },
            ),
        ),
        Section(
            "Window",
            listOfNotNull(
                "Active schedule" to (active?.let { "#${it.id}" } ?: "none"),
                "Started window" to (started?.let { moment.format(it.window) } ?: "none"),
                started?.let { "Changed" to Events.changes(it.changes) },
                started?.let { "Meant to change" to Events.changes(it.wanted) },
                started?.let { "Already set" to Events.changes(it.already) },
                app.engine.state.wifiKeptUntil?.let { "Wi-Fi kept on until" to moment.format(it) },
            ),
        ),
        Section(
            "Settings",
            listOf(
                "Theme" to settings.theme.name,
                "Dynamic color" to if (hasDynamicColor) Events.onOff(settings.dynamicColor) else "UNSUPPORTED",
                "Postpone" to (settings.postponeMinutes.takeIf { it > 0 }?.let { "$it min" } ?: "OFF"),
            ),
        ),
    ) + schedules.map { schedule ->
        val name = if (schedule.name.isBlank()) "" else " “${schedule.name}”"
        Section("Schedule #${schedule.id}$name", scheduleRows(schedule, zoned))
    }
}

private fun scheduleRows(schedule: Schedule, now: ZonedDateTime): List<Pair<String, String>> = buildList {
    fun dates(dates: Collection<LocalDate>) = dates.sorted().joinToString(" ")
    add("Enabled" to Events.yesNo(schedule.enabled))
    add("Window" to "${ScheduleTime.format(schedule.start)}–${ScheduleTime.format(schedule.end)}")
    add(
        "Days" to when {
            schedule.once -> "ONCE ${schedule.date ?: "no date"}"
            else -> schedule.days.sorted().joinToString(" ") { it.name.take(3) }
        },
    )
    add("Wi-Fi at start" to if (schedule.disableWifi) "OFF" else "UNCHANGED")
    add("Wi-Fi at end" to if (schedule.disableWifi && schedule.enableWifi) "ON" else "UNCHANGED")
    if (!schedule.enabled) return@buildList
    if (schedule.delayed.isNotEmpty()) {
        add("Delayed" to schedule.delayed.toSortedMap().entries.joinToString(" ") { "${it.key}+${it.value}min" })
    }
    val skipped = schedule.skipped - schedule.endedEarly
    if (skipped.isNotEmpty()) add("Skipped" to dates(skipped))
    if (schedule.endedEarly.isNotEmpty()) add("Ended early" to dates(schedule.endedEarly))
    add("Next start" to (ScheduleResolver.nextStart(schedule, now)?.let(moment::format) ?: "none"))
    add("Next end" to (ScheduleResolver.nextEnd(schedule, now)?.let(moment::format) ?: "none"))
}
