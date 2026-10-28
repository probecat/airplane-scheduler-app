package io.github.probecat.airplanescheduler.mock

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Ending
import io.github.probecat.airplanescheduler.data.ThemeMode
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.Notifier
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.SwitchRow
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.rememberNotificationRequest
import io.github.probecat.airplanescheduler.ui.theme.hasDynamicColor
import java.util.Locale

// Always provides the locals, so turning mock data on or off doesn't rebuild the screens below.
@Composable
fun MockDisplay(content: @Composable () -> Unit) {
    val mock = activeMock()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val locale = mock?.locale
    val mocked = remember(configuration, locale) {
        if (locale == null) {
            configuration
        } else {
            Configuration(configuration).apply { setLocale(Locale.forLanguageTag(locale)) }
        }
    }
    CompositionLocalProvider(
        LocalConfiguration provides mocked,
        LocalDensity provides (mock?.fontScale?.let { Density(density.density, it) } ?: density),
        content = content,
    )
}

@Composable
fun MockScreen(viewModel: AppViewModel, onOpen: (Destination) -> Unit, onBack: () -> Unit) {
    val app = App.from(LocalContext.current)
    val config by Mock.config.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val format = rememberFormat()
    val on = config.enabled
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    fun update(transform: (MockConfig) -> MockConfig) = Mock.update(app, transform)

    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requestNotifications = rememberNotificationRequest(real = true) { granted ->
        if (granted) pending?.invoke()
        pending = null
    }

    fun post(action: () -> Unit) {
        update { it.copy(notifications = true) }
        if (NotificationManagerCompat.from(app).areNotificationsEnabled()) {
            action()
        } else {
            pending = action
            requestNotifications()
        }
    }

    Subpage("UI states", onBack) {
        SwitchRow(icon = null, title = "Mock data", checked = on) { enabled ->
            viewModel.clearDrafts()
            update { it.copy(enabled = enabled) }
        }
        Note("Uses its own schedules and settings. Alarms pause while it's on.")

        SectionHeader("Scenarios")
        ScenarioPicker(scenarios.find { it.id == config.scenario }) { scenario ->
            scenario.apply(app, viewModel)
            onOpen(scenario.destination)
        }

        SectionHeader("Device")
        Choice(
            "Shizuku",
            MockLaunch.SHIZUKU.values.zip(listOf("Ready", "Offline", "No access")),
            config.shizuku,
            on,
        ) { state -> update { it.copy(shizuku = state) } }
        SwitchRow(null, "Shizuku app installed", config.shizukuInstalled, on) { installed ->
            update { it.copy(shizukuInstalled = installed) }
        }
        SwitchRow(null, "Sui instead of Shizuku", config.sui, on) { sui ->
            update { it.copy(sui = sui) }
        }
        SwitchRow(null, "Notifications allowed", config.notifications, on) { allowed ->
            update { it.copy(notifications = allowed) }
        }
        SwitchRow(null, "Battery optimization (Android 9-11)", config.batteryOptimized, on) { optimized ->
            update { it.copy(batteryOptimized = optimized) }
        }

        SectionHeader("Notifications")
        Note("Posts real ones for the mock schedules, clock and Shizuku. Android's permission is needed.")
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockButton("Notices", on) {
                post {
                    if (!Notifier.repost(app)) {
                        Toast.makeText(app, "No schedule has a notice at this time", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            for ((label, report) in endReports) {
                ClockButton(label, on) { post { report(app) } }
            }
        }

        SectionHeader("Clock")
        Note(
            "${format.dayName(now.dayOfWeek)}, ${format.time(now.hour * 60 + now.minute)}" +
                if (config.clockOffset == 0L) " (real time)" else "",
        )
        Choice(null, format.week.map { it to format.dayLetter(it) }, now.dayOfWeek, on) { day ->
            val days = format.week.indexOf(day) - format.week.indexOf(now.dayOfWeek)
            update { it.copy(clockOffset = it.clockOffset + days * 24 * 60L) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockButton("Set time", on) { pickingTime = true }
            ClockButton("−1 h", on) { update { it.copy(clockOffset = it.clockOffset - 60) } }
            ClockButton("+1 h", on) { update { it.copy(clockOffset = it.clockOffset + 60) } }
            ClockButton("Real", on) { update { it.copy(clockOffset = 0) } }
        }

        SectionHeader("Display")
        val themes = ThemeMode.entries.map { it to stringResource(it.label).substringBefore(" ") }
        Choice("Theme", themes, settings.theme, true) { theme ->
            viewModel.updateSettings { it.copy(theme = theme) }
        }
        SwitchRow(null, "Dynamic color", settings.dynamicColor, hasDynamicColor) { dynamic ->
            viewModel.updateSettings { it.copy(dynamicColor = dynamic) }
        }
        Choice(
            "Time format",
            listOf(null to "System", false to "12 h", true to "24 h"),
            config.is24Hour,
            on,
        ) { is24Hour ->
            update { it.copy(is24Hour = is24Hour) }
        }
        Choice(
            "Text size",
            listOf(null to "System", 0.85f to "85%", 1.3f to "130%", 2f to "200%"),
            config.fontScale,
            on,
        ) { scale -> update { it.copy(fontScale = scale) } }
        Choice(
            "Animations",
            listOf(null to "System", 0f to "Off", 1f to "1×", 5f to "5×"),
            config.animationScale,
            on,
        ) { scale -> update { it.copy(animationScale = scale) } }
        Choice(
            "Locale",
            listOf(null to "System", "en-US" to "US", "en-GB" to "UK", "de-DE" to "DE", "ja-JP" to "JA"),
            config.locale,
            on,
        ) { locale -> update { it.copy(locale = locale) } }

        SectionHeader("Scripting")
        SelectionContainer {
            Text(
                MockLaunch.USAGE,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }

    if (pickingTime) {
        TimeDialog(now.hour * 60 + now.minute, format.is24Hour, onDismiss = { pickingTime = false }) { minute ->
            val target = now.toLocalDate().atTime(minute / 60, minute % 60)
            update { it.copy(clockOffset = Mock.offsetTo(target)) }
            pickingTime = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenarioPicker(applied: Scenario?, onApply: (Scenario) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { open = it },
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = applied?.title ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Last applied") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            scenarios.forEach { scenario ->
                DropdownMenuItem(
                    text = { Text(scenario.title) },
                    trailingIcon = {
                        Text(
                            scenario.id,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    onClick = {
                        open = false
                        onApply(scenario)
                    },
                )
            }
        }
    }
}

private val endReports: List<Pair<String, (App) -> Unit>> = listOf(
    "Ended" to { app -> ended(app, Ending(Changes.NONE, wifiBackOn = true)) },
    "Already on" to
        { app -> ended(app, Ending(Changes.NONE, airplaneWasOn = true, airplaneStaysOn = true, wifiWasOff = true)) },
    "Turned off" to { app -> ended(app, Ending(Changes.NONE, airplaneTurnedOff = true, wifiBackOn = true)) },
    "Missed start" to { app -> ended(app, Ending(Changes.NONE, missedStart = true)) },
    "Failed end" to { app -> ended(app, Ending(Changes(airplane = true, wifi = true))) },
    "Missed end" to { app -> Notifier.missedEnd(app, app.schedules.all.firstOrNull(), Changes(true, false)) },
)

private fun ended(app: App, ending: Ending) {
    val schedule = app.schedules.all.firstOrNull()
    val report = schedule?.let { Notifier.report(app, it) } ?: Notifier.Report(0, "A schedule")
    Notifier.ended(app, report, ending)
}

// Narrower than the default padding, so four fit in a row.
@Composable
private fun ClockButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 16.dp)) {
        Text(label)
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun <T> Choice(
    title: String?,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    enabled = enabled,
                    icon = {},
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, is24Hour: Boolean, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(minute / 60, minute % 60, is24Hour)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Mock time", style = MaterialTheme.typography.labelMedium) },
    ) { TimePicker(state) }
}
