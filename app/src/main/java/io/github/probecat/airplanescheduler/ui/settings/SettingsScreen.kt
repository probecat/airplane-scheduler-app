package io.github.probecat.airplanescheduler.ui.settings

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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.BuildConfig
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.data.ThemeMode
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.exactAlarmSettings
import io.github.probecat.airplanescheduler.system.exactAlarmsAllowed
import io.github.probecat.airplanescheduler.system.exactAlarmsDeniable
import io.github.probecat.airplanescheduler.system.notificationsAllowed
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.LinkRow
import io.github.probecat.airplanescheduler.ui.SOURCE_URL
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.SettingRow
import io.github.probecat.airplanescheduler.ui.ShizukuDialog
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.SwitchRow
import io.github.probecat.airplanescheduler.ui.notificationSettings
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.rememberNotificationRequest
import io.github.probecat.airplanescheduler.ui.theme.hasDynamicColor

@Composable
fun SettingsScreen(viewModel: AppViewModel, onDebug: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val format = rememberFormat()
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    var exactAlarms by remember { mutableStateOf(exactAlarmsAllowed(context)) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var choosingPostpone by rememberSaveable { mutableStateOf(false) }
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val shizukuApp = ShizukuStatus.launchIntent(context)
    var explaining by rememberSaveable { mutableStateOf<ShizukuState?>(null) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = notificationsAllowed(context)
        exactAlarms = exactAlarmsAllowed(context)
    }
    val requestNotifications = rememberNotificationRequest { notifications = notificationsAllowed(context) }

    Subpage(stringResource(R.string.settings), onBack, actions = { SettingsMenu(onReset = { resetting = true }) }) {
        SectionHeader(stringResource(R.string.settings_appearance))
        SettingRow(
            icon = R.drawable.ic_contrast,
            title = stringResource(R.string.settings_theme),
            text = stringResource(settings.theme.label),
            onClick = { choosingTheme = true },
        )
        if (hasDynamicColor) {
            SwitchRow(
                icon = R.drawable.ic_palette,
                title = stringResource(R.string.settings_dynamic_color),
                checked = settings.dynamicColor,
            ) { on -> viewModel.updateSettings { it.copy(dynamicColor = on) } }
        }

        SectionHeader(stringResource(R.string.settings_schedules))
        SettingRow(
            icon = R.drawable.ic_snooze,
            title = stringResource(R.string.settings_postpone),
            text = postponeLabel(settings.postponeMinutes, format),
            onClick = { choosingPostpone = true },
        )

        SectionHeader(stringResource(R.string.settings_permissions))
        SettingRow(
            icon = when (shizuku) {
                ShizukuState.READY -> R.drawable.ic_check_circle
                ShizukuState.OFFLINE -> R.drawable.ic_error
                ShizukuState.PERMISSION_NEEDED -> R.drawable.ic_lock
            },
            title = stringResource(R.string.settings_shizuku),
            text = stringResource(
                when (shizuku) {
                    ShizukuState.READY -> R.string.settings_shizuku_ready
                    ShizukuState.OFFLINE ->
                        if (shizukuApp != null) R.string.status_not_running else R.string.status_not_installed
                    ShizukuState.PERMISSION_NEEDED -> R.string.status_not_allowed
                },
            ),
            trailing = {
                when {
                    shizuku == ShizukuState.PERMISSION_NEEDED -> TextButton(onClick = {
                        if (!viewModel.requestShizuku()) explaining = shizuku
                    }) { Text(stringResource(R.string.action_allow)) }
                    shizukuApp != null -> TextButton(onClick = { context.startActivity(shizukuApp) }) {
                        Text(
                            stringResource(
                                if (shizuku ==
                                    ShizukuState.READY
                                ) {
                                    R.string.action_manage
                                } else {
                                    R.string.action_open
                                },
                            ),
                        )
                    }
                    else -> TextButton(onClick = { context.startActivity(ShizukuStatus.repository()) }) {
                        Text(stringResource(R.string.action_get))
                    }
                }
            },
        )
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
                icon = R.drawable.ic_alarm,
                title = stringResource(R.string.settings_alarms),
                text = stringResource(if (exactAlarms) R.string.status_allowed else R.string.settings_alarms_denied),
                trailing = {
                    TextButton(onClick = { context.startActivity(exactAlarmSettings(context)) }) {
                        Text(stringResource(if (exactAlarms) R.string.action_manage else R.string.action_allow))
                    }
                },
            )
        }

        SectionHeader(stringResource(R.string.settings_about))
        SettingRow(
            icon = R.drawable.ic_info,
            title = stringResource(R.string.settings_version),
            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onClick = onDebug,
        )
        LinkRow(R.drawable.ic_code, stringResource(R.string.settings_source), SOURCE_URL)
    }

    explaining?.let { state ->
        ShizukuDialog(state, onHelp = null, onDismiss = { explaining = null })
    }

    if (resetting) {
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSettings()
                    resetting = false
                }) { Text(stringResource(R.string.action_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { resetting = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (choosingPostpone) {
        Choices(
            title = stringResource(R.string.settings_postpone),
            options = POSTPONE_MINUTES.map { it to postponeLabel(it, format) },
            selected = settings.postponeMinutes,
            onSelect = { minutes ->
                viewModel.updateSettings { it.copy(postponeMinutes = minutes) }
                choosingPostpone = false
            },
            onDismiss = { choosingPostpone = false },
        )
    }

    if (choosingTheme) {
        Choices(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to stringResource(it.label) },
            selected = settings.theme,
            onSelect = { theme ->
                viewModel.updateSettings { it.copy(theme = theme) }
                choosingTheme = false
            },
            onDismiss = { choosingTheme = false },
        )
    }
}

@Composable
private fun SettingsMenu(onReset: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_reset)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_settings_backup_restore), contentDescription = null)
                },
                onClick = {
                    open = false
                    onReset()
                },
            )
        }
    }
}

private val POSTPONE_MINUTES = listOf(0, 15, 30, 45, 60)

@Composable
private fun postponeLabel(minutes: Int, format: Format) =
    if (minutes == 0) stringResource(R.string.settings_postpone_off) else format.duration(minutes)

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
