package io.github.probecat.airplanescheduler.ui.settings

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.BuildConfig
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.data.Backend
import io.github.probecat.airplanescheduler.data.Settings
import io.github.probecat.airplanescheduler.data.ThemeMode
import io.github.probecat.airplanescheduler.system.Access
import io.github.probecat.airplanescheduler.system.AccessState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.exactAlarmSettings
import io.github.probecat.airplanescheduler.system.exactAlarmsAllowed
import io.github.probecat.airplanescheduler.system.exactAlarmsDeniable
import io.github.probecat.airplanescheduler.system.notificationsAllowed
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.LinkRow
import io.github.probecat.airplanescheduler.ui.MenuItem
import io.github.probecat.airplanescheduler.ui.OverflowMenu
import io.github.probecat.airplanescheduler.ui.SOURCE_URL
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.SettingRow
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.SwitchRow
import io.github.probecat.airplanescheduler.ui.icon
import io.github.probecat.airplanescheduler.ui.notificationSettings
import io.github.probecat.airplanescheduler.ui.rememberAccessRequest
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.rememberNotificationRequest
import io.github.probecat.airplanescheduler.ui.theme.hasDynamicColor

private enum class Dialog { THEME, POSTPONE, ALERTS, BACKEND, RESET, BEHAVIOR }

@Composable
fun SettingsScreen(viewModel: AppViewModel, onDebug: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val access by viewModel.access.collectAsStateWithLifecycle()
    val format = rememberFormat()
    val requestAccess = rememberAccessRequest(viewModel)
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    val update: ((Settings) -> Settings) -> Unit = viewModel::updateSettings

    val menu = listOf(
        MenuItem(stringResource(R.string.settings_reset), R.drawable.ic_reset_settings) {
            dialog = Dialog.RESET
        },
    )
    Subpage(stringResource(R.string.settings), onBack, modifier, actions = { OverflowMenu(menu) }) {
        AppearanceSection(
            settings,
            onTheme = { dialog = Dialog.THEME },
            onDynamicColor = { on -> update { it.copy(dynamicColor = on) } },
        )
        SchedulesSection(
            settings,
            format,
            onAlerts = { dialog = Dialog.ALERTS },
            onPostpone = { dialog = Dialog.POSTPONE },
            onBehavior = { dialog = Dialog.BEHAVIOR },
        )
        PermissionsSection(settings, access, requestAccess, onBackend = { dialog = Dialog.BACKEND })
        AboutSection(onDebug)
    }

    dialog?.let { shown ->
        SettingsDialog(
            shown,
            settings,
            format,
            onUpdate = update,
            onReset = viewModel::resetSettings,
            // Asks right away, so the root prompt comes while the user is choosing root.
            onBackendChange = { requestAccess(viewModel.access.value) },
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun AppearanceSection(
    settings: Settings,
    onTheme: () -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SectionHeader(stringResource(R.string.settings_appearance))
    SettingRow(
        icon = R.drawable.ic_contrast,
        title = stringResource(R.string.settings_theme),
        text = stringResource(settings.theme.label),
        onClick = onTheme,
    )
    if (hasDynamicColor) {
        SwitchRow(
            icon = R.drawable.ic_palette,
            title = stringResource(R.string.settings_dynamic_color),
            checked = settings.dynamicColor,
            onChange = onDynamicColor,
        )
    }
}

@Composable
private fun SchedulesSection(
    settings: Settings,
    format: Format,
    onAlerts: () -> Unit,
    onPostpone: () -> Unit,
    onBehavior: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier) {
    SectionHeader(stringResource(R.string.settings_schedules))
    SettingRow(
        icon = R.drawable.ic_alarm,
        title = stringResource(R.string.settings_alerts),
        text = minutesLabel(settings.alertMinutes, format),
        onClick = onAlerts,
    )
    SettingRow(
        icon = R.drawable.ic_snooze,
        title = stringResource(R.string.settings_postpone),
        text = minutesLabel(settings.postponeMinutes, format),
        onClick = onPostpone,
    )
    SettingRow(
        icon = R.drawable.ic_asterisk,
        title = stringResource(R.string.settings_behavior),
        text = stringResource(behaviorLabel(settings.safeMode)),
        trailing = { TextButton(onClick = onBehavior) { Text(stringResource(R.string.action_change)) } },
    )
}

@Composable
private fun PermissionsSection(
    settings: Settings,
    access: Access,
    onRequestAccess: (Access) -> Unit,
    onBackend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    var exactAlarms by remember { mutableStateOf(exactAlarmsAllowed(context)) }
    var shizukuApp by remember { mutableStateOf(ShizukuStatus.launchIntent(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = notificationsAllowed(context)
        exactAlarms = exactAlarmsAllowed(context)
        shizukuApp = ShizukuStatus.launchIntent(context)
    }
    val requestNotifications = rememberNotificationRequest { notifications = notificationsAllowed(context) }
    Column(modifier) {
        SectionHeader(stringResource(R.string.settings_permissions))
        // First, as the mode decides which access the next row shows.
        SettingRow(
            icon = R.drawable.ic_shield,
            title = stringResource(R.string.settings_backend),
            text = stringResource(backendLabel(settings.backend)),
            trailing = { TextButton(onClick = onBackend) { Text(stringResource(R.string.action_change)) } },
        )
        AccessRow(access, shizukuApp, onRequestAccess)
        SettingRow(
            icon = R.drawable.ic_notifications,
            title = stringResource(R.string.settings_notifications),
            text = stringResource(if (notifications) R.string.status_allowed else R.string.status_not_allowed),
            trailing = {
                if (notifications) {
                    TextButton(onClick = { context.startActivity(notificationSettings(context)) }) {
                        Text(stringResource(R.string.action_manage))
                    }
                } else {
                    TextButton(onClick = requestNotifications) { Text(stringResource(R.string.action_allow)) }
                }
            },
        )
        if (exactAlarmsDeniable) {
            SettingRow(
                icon = R.drawable.ic_alarm_on,
                title = stringResource(R.string.settings_alarms),
                text = stringResource(if (exactAlarms) R.string.status_allowed else R.string.status_not_allowed),
                trailing = {
                    TextButton(onClick = { context.startActivity(exactAlarmSettings(context)) }) {
                        Text(stringResource(if (exactAlarms) R.string.action_manage else R.string.action_allow))
                    }
                },
            )
        }
    }
}

@Composable
private fun AboutSection(onDebug: () -> Unit, modifier: Modifier = Modifier) = Column(modifier) {
    SectionHeader(stringResource(R.string.settings_about))
    SettingRow(
        icon = R.drawable.ic_info,
        title = stringResource(R.string.settings_version),
        text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        onClick = onDebug,
    )
    LinkRow(R.drawable.ic_code, stringResource(R.string.settings_source), SOURCE_URL)
}

@Composable
private fun SettingsDialog(
    dialog: Dialog,
    settings: Settings,
    format: Format,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onReset: () -> Unit,
    onBackendChange: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        Dialog.BEHAVIOR -> Choices(
            title = stringResource(R.string.settings_behavior),
            options = listOf(true, false).map { it to stringResource(behaviorLabel(it)) },
            selected = settings.safeMode,
            onSelect = { safe ->
                onUpdate { it.copy(safeMode = safe) }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
        Dialog.RESET -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    onDismiss()
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        Dialog.POSTPONE -> MinutesChoices(R.string.settings_postpone, settings.postponeMinutes, format, onDismiss) {
            onUpdate { old -> old.copy(postponeMinutes = it) }
        }
        Dialog.ALERTS -> MinutesChoices(R.string.settings_alerts, settings.alertMinutes, format, onDismiss) {
            onUpdate { old -> old.copy(alertMinutes = it) }
        }
        Dialog.BACKEND -> Choices(
            title = stringResource(R.string.settings_backend),
            options = Backend.entries.map { it to stringResource(backendLabel(it)) },
            selected = settings.backend,
            onSelect = { backend ->
                onUpdate { it.copy(backend = backend) }
                onDismiss()
                onBackendChange()
            },
            onDismiss = onDismiss,
        )
        Dialog.THEME -> Choices(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(it.label) },
            selected = settings.theme,
            onSelect = { theme ->
                onUpdate { it.copy(theme = theme) }
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun AccessRow(access: Access, shizukuApp: Intent?, request: (Access) -> Unit) {
    val context = LocalContext.current
    SettingRow(
        icon = access.state.icon,
        title = stringResource(
            when (access.kind) {
                Access.Kind.SHIZUKU -> R.string.settings_shizuku
                Access.Kind.SUI -> R.string.settings_sui
                Access.Kind.ROOT -> R.string.settings_root
            },
        ),
        text = stringResource(
            when (access.state) {
                AccessState.READY ->
                    if (access.kind == Access.Kind.ROOT) R.string.status_allowed else R.string.settings_shizuku_ready
                AccessState.OFFLINE ->
                    if (shizukuApp != null) R.string.status_not_running else R.string.status_not_installed
                AccessState.PERMISSION_NEEDED -> R.string.status_not_allowed
            },
        ),
        trailing = when {
            access.state == AccessState.PERMISSION_NEEDED -> {
                { TextButton(onClick = { request(access) }) { Text(stringResource(R.string.action_allow)) } }
            }
            access.kind != Access.Kind.SHIZUKU -> null
            shizukuApp != null -> {
                {
                    TextButton(onClick = { context.startActivity(shizukuApp) }) {
                        Text(stringResource(if (access.ready) R.string.action_manage else R.string.action_open))
                    }
                }
            }
            else -> {
                {
                    TextButton(onClick = { context.startActivity(ShizukuStatus.repository()) }) {
                        Text(stringResource(R.string.action_get))
                    }
                }
            }
        },
    )
}

private fun behaviorLabel(safe: Boolean): Int = if (safe) R.string.behavior_conservative else R.string.behavior_strict

// Sui stands in for Shizuku when it is installed.
private fun backendLabel(backend: Backend): Int =
    if (backend == Backend.SHIZUKU && ShizukuStatus.sui) R.string.backend_sui else backend.label

// Upcoming notices stop at 60, as the notification's Skip and Postpone work only in the hour before a start.
private val MINUTES = listOf(0, 15, 30, 45, 60)

@Composable
private fun MinutesChoices(
    @StringRes title: Int,
    selected: Int,
    format: Format,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) = Choices(
    title = stringResource(title),
    options = MINUTES.map { it to minutesLabel(it, format) },
    selected = selected,
    onSelect = { minutes ->
        onSelect(minutes)
        onDismiss()
    },
    onDismiss = onDismiss,
)

@Composable
private fun minutesLabel(minutes: Int, format: Format) =
    if (minutes == 0) stringResource(R.string.settings_off) else format.duration(minutes)

@Composable
private fun <T> Choices(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, role = Role.RadioButton) { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
