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

    Subpage("Settings", onBack, actions = { SettingsMenu(onReset = { resetting = true }) }) {
        SectionHeader("Appearance")
        SettingRow(
            icon = R.drawable.ic_contrast,
            title = "Theme",
            text = settings.theme.label,
            onClick = { choosingTheme = true },
        )
        SwitchRow(
            icon = R.drawable.ic_palette,
            title = "Dynamic color",
            checked = settings.dynamicColor,
        ) { on -> viewModel.updateSettings { it.copy(dynamicColor = on) } }

        SectionHeader("Reminders")
        // The reminder can't work without notifications, so it shows as off until they're allowed, and
        // switching it on asks for them.
        SwitchRow(
            icon = R.drawable.ic_notifications_active,
            title = "Remind me if Shizuku is off",
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

        SectionHeader("Permissions")
        SettingRow(
            icon = when (shizuku) {
                ShizukuState.READY -> R.drawable.ic_check_circle
                ShizukuState.OFFLINE -> R.drawable.ic_error
                ShizukuState.PERMISSION_NEEDED -> R.drawable.ic_lock
            },
            title = "Shizuku",
            text = when (shizuku) {
                ShizukuState.READY -> "Ready"
                ShizukuState.OFFLINE -> if (shizukuApp != null) "Not running" else "Not installed"
                ShizukuState.PERMISSION_NEEDED -> "Not allowed"
            },
            trailing = {
                when {
                    shizuku == ShizukuState.PERMISSION_NEEDED -> TextButton(onClick = {
                        if (!viewModel.requestShizuku()) explaining = shizuku
                    }) { Text("Allow") }
                    shizukuApp != null -> TextButton(onClick = { context.startActivity(shizukuApp) }) {
                        Text(if (shizuku == ShizukuState.READY) "Manage" else "Open")
                    }
                    else -> TextButton(onClick = { context.startActivity(ShizukuStatus.repository()) }) { Text("Get") }
                }
            },
        )
        SettingRow(
            icon = R.drawable.ic_notifications,
            title = "Notifications",
            text = if (notifications) "Allowed" else "Not allowed",
            trailing = {
                TextButton(onClick = { context.startActivity(notificationSettings(context)) }) {
                    Text(if (notifications) "Manage" else "Allow")
                }
            },
        )

        SectionHeader("About")
        SettingRow(
            icon = R.drawable.ic_info,
            title = "Version",
            text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onClick = onDebug,
        )
        LinkRow(R.drawable.ic_code, "Source code", SOURCE_URL)
    }

    explaining?.let { state ->
        ShizukuDialog(state, onHelp = null, onDismiss = { explaining = null })
    }

    if (resetting) {
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text("Reset preferences?") },
            text = { Text("Settings go back to their defaults. Schedules are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSettings()
                    resetting = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancel") } },
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
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Reset preferences") },
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
        title = { Text("Theme") },
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
                            theme.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun notificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
