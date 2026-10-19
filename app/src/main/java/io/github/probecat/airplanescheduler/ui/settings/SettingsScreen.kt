package io.github.probecat.airplanescheduler.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import io.github.probecat.airplanescheduler.mock.Mock
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.notificationsAllowed
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.LinkRow
import io.github.probecat.airplanescheduler.ui.SOURCE_URL
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.SettingRow
import io.github.probecat.airplanescheduler.ui.ShizukuDialog
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.SwitchRow

@Composable
fun SettingsScreen(viewModel: AppViewModel, onDebug: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var notifications by remember { mutableStateOf(notificationsAllowed(context)) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    // Set while the app's notification settings are open for the reminder.
    var allowingInSettings by rememberSaveable { mutableStateOf(false) }
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val shizukuApp = ShizukuStatus.launchIntent(context)
    var explaining by rememberSaveable { mutableStateOf<ShizukuState?>(null) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = notificationsAllowed(context)
        if (allowingInSettings && notifications) viewModel.updateSettings { it.copy(remindShizuku = true) }
        allowingInSettings = false
    }
    val activity = LocalActivity.current
    fun rationale() = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val requestNotifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notifications = notificationsAllowed(context)
            if (granted) {
                viewModel.updateSettings { it.copy(remindShizuku = true) }
            } else if (!rationaleBefore && !rationale()) {
                // Android showed no dialog, as it does after repeated denials, so only the settings can allow it.
                allowingInSettings = true
                context.startActivity(notificationSettings(context))
            }
        }

    Subpage(stringResource(R.string.settings), onBack, actions = { SettingsMenu(onReset = { resetting = true }) }) {
        SectionHeader(stringResource(R.string.settings_appearance))
        SettingRow(
            icon = R.drawable.ic_contrast,
            title = stringResource(R.string.settings_theme),
            text = stringResource(settings.theme.label),
            onClick = { choosingTheme = true },
        )
        SwitchRow(
            icon = R.drawable.ic_palette,
            title = stringResource(R.string.settings_dynamic_color),
            checked = settings.dynamicColor,
        ) { on -> viewModel.updateSettings { it.copy(dynamicColor = on) } }

        SectionHeader(stringResource(R.string.settings_reminders))
        // The reminder can't work without notifications, so it shows as off until they're allowed, and
        // switching it on asks for them.
        SwitchRow(
            icon = R.drawable.ic_notifications_active,
            title = stringResource(R.string.settings_remind_shizuku),
            checked = settings.remindShizuku && notifications,
        ) { on ->
            when {
                !on || notifications -> viewModel.updateSettings { it.copy(remindShizuku = on) }
                // Android's dialog can't be mocked, so mock data acts as if notifications were allowed.
                Mock.active -> {
                    Mock.update(App.from(context)) { it.copy(notifications = true) }
                    notifications = true
                    viewModel.updateSettings { it.copy(remindShizuku = true) }
                }
                else -> {
                    rationaleBefore = rationale()
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

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
                TextButton(onClick = { context.startActivity(notificationSettings(context)) }) {
                    Text(stringResource(if (notifications) R.string.action_manage else R.string.action_allow))
                }
            },
        )

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

    if (choosingTheme) {
        ThemeDialog(
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

@Composable
private fun ThemeDialog(selected: ThemeMode, onSelect: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = theme == selected, role = Role.RadioButton) { onSelect(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == selected, onClick = null)
                        Text(
                            stringResource(theme.label),
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

private fun notificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
