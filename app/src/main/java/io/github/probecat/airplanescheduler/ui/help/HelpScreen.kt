package io.github.probecat.airplanescheduler.ui.help

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.system.Access
import io.github.probecat.airplanescheduler.system.AccessState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.batteryOptimizationDelays
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.ISSUES_URL
import io.github.probecat.airplanescheduler.ui.LinkRow
import io.github.probecat.airplanescheduler.ui.MenuItem
import io.github.probecat.airplanescheduler.ui.OverflowMenu
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.SettingRow
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.icon
import io.github.probecat.airplanescheduler.ui.rememberAccessRequest
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.theme.LocalSuccessColors

@Composable
private fun topic(@StringRes question: Int, @StringRes answer: Int, vararg args: Any) =
    stringResource(question) to stringResource(answer, *args)

@Composable
private fun schedules(format: Format) = listOf(
    topic(R.string.help_start_end_q, R.string.help_start_end_a),
    topic(R.string.help_wifi_q, R.string.help_wifi_a),
    topic(R.string.help_overnight_q, R.string.help_overnight_a, format.time(23 * 60), format.time(7 * 60)),
    topic(R.string.help_once_q, R.string.help_once_a),
    topic(R.string.help_skip_q, R.string.help_skip_a),
    topic(R.string.help_pause_q, R.string.help_pause_a),
    topic(R.string.help_change_active_q, R.string.help_change_active_a),
    topic(R.string.help_overlap_q, R.string.help_overlap_a),
)

@Composable
private fun troubleshooting(format: Format, kind: Access.Kind): List<Pair<String, String>> {
    var shizukuStopped = stringResource(R.string.help_shizuku_stopped_a)
    // Wireless debugging, which Shizuku starts with, came in Android 11.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        shizukuStopped = format.sentences(shizukuStopped, stringResource(R.string.help_shizuku_stopped_legacy))
    }
    return listOfNotNull(
        topic(
            R.string.help_missed_q,
            when (kind) {
                Access.Kind.SHIZUKU -> R.string.help_missed_a
                Access.Kind.SUI -> R.string.help_missed_a_sui
                Access.Kind.ROOT -> R.string.help_missed_a_root
            },
        ),
        if (batteryOptimizationDelays) topic(R.string.help_late_q, R.string.help_late_a) else null,
        // Sui and root run from boot, so these are about Shizuku only.
        if (kind != Access.Kind.SHIZUKU) null else stringResource(R.string.help_shizuku_stopped_q) to shizukuStopped,
        if (kind != Access.Kind.SHIZUKU) null else topic(R.string.help_root_q, R.string.help_root_a),
    )
}

@Composable
fun HelpScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val format = rememberFormat()
    val access by viewModel.access.collectAsStateWithLifecycle()
    Subpage(stringResource(R.string.help), onBack) {
        SectionHeader(
            stringResource(
                when (access.kind) {
                    Access.Kind.SHIZUKU -> R.string.help_setup
                    Access.Kind.SUI -> R.string.help_setup_sui
                    Access.Kind.ROOT -> R.string.help_setup_root
                },
            ),
        )
        Text(
            stringResource(
                when (access.kind) {
                    Access.Kind.SHIZUKU -> R.string.help_setup_text
                    Access.Kind.SUI -> R.string.help_setup_text_sui
                    Access.Kind.ROOT -> R.string.help_setup_text_root
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        when (access.kind) {
            Access.Kind.SHIZUKU -> SetupSteps(viewModel)
            Access.Kind.SUI -> SuiSteps(viewModel)
            Access.Kind.ROOT -> RootSteps(viewModel)
        }

        SectionHeader(stringResource(R.string.help_schedules))
        schedules(format).forEach { (question, answer) -> Topic(question, answer) }

        SectionHeader(stringResource(R.string.help_troubleshooting))
        troubleshooting(format, access.kind).forEach { (question, answer) -> Topic(question, answer) }
        LinkRow(R.drawable.ic_bug_report, stringResource(R.string.help_report), ISSUES_URL)
    }
}

@Composable
private fun SetupSteps(viewModel: AppViewModel) {
    val context = LocalContext.current
    val access by viewModel.access.collectAsStateWithLifecycle()
    var app by remember { mutableStateOf(ShizukuStatus.launchIntent(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { app = ShizukuStatus.launchIntent(context) }
    val requestAccess = rememberAccessRequest(viewModel)

    val installed = app != null
    val running = installed && access.state != AccessState.OFFLINE
    val allowed = access.ready
    Step(
        R.drawable.ic_counter_1,
        R.string.help_install,
        if (installed) R.string.help_installed else R.string.status_not_installed,
        installed,
    ) {
        TextButton(onClick = { context.startActivity(ShizukuStatus.repository()) }) {
            Text(stringResource(R.string.action_get))
        }
    }
    Step(
        R.drawable.ic_counter_2,
        R.string.help_start,
        if (running) R.string.help_running else R.string.status_not_running,
        running,
    ) {
        if (installed) {
            TextButton(onClick = { app?.let(context::startActivity) }) { Text(stringResource(R.string.action_open)) }
        }
    }
    Step(
        R.drawable.ic_counter_3,
        R.string.help_grant,
        if (allowed) R.string.status_allowed else R.string.status_not_allowed,
        allowed,
    ) {
        if (running) {
            TextButton(onClick = { requestAccess(access) }) { Text(stringResource(R.string.action_allow)) }
        }
    }
}

@Composable
private fun SuiSteps(viewModel: AppViewModel) {
    Step(R.drawable.ic_counter_1, R.string.help_install_sui, R.string.help_installed, done = true) {}
    GrantStep(viewModel, R.drawable.ic_counter_2)
}

@Composable
private fun RootSteps(viewModel: AppViewModel) {
    GrantStep(viewModel, R.drawable.ic_counter_1)
}

@Composable
private fun GrantStep(viewModel: AppViewModel, number: Int) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val requestAccess = rememberAccessRequest(viewModel)
    Step(
        number,
        R.string.help_grant,
        if (access.ready) R.string.status_allowed else R.string.status_not_allowed,
        access.ready,
    ) {
        TextButton(onClick = { requestAccess(access) }) { Text(stringResource(R.string.action_allow)) }
    }
}

@Composable
private fun Step(
    number: Int,
    @StringRes title: Int,
    @StringRes status: Int,
    done: Boolean,
    action: @Composable () -> Unit,
) {
    SettingRow(
        icon = if (done) R.drawable.ic_check_circle else number,
        title = stringResource(title),
        text = stringResource(status),
        tint = if (done) LocalSuccessColors.current.color else MaterialTheme.colorScheme.error,
        trailing = if (done) null else action,
    )
}

@Composable
private fun Topic(question: String, answer: String) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f)
    val clickLabel = stringResource(if (open) R.string.help_collapse else R.string.help_expand)
    val state = stringResource(if (open) R.string.help_expanded else R.string.help_collapsed)
    ListItem(
        headlineContent = { Text(question) },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, Modifier.rotate(turn))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClickLabel = clickLabel) { open = !open }
            .semantics { stateDescription = state }
            .padding(horizontal = 8.dp),
    )
    if (open) {
        Text(
            answer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        )
    }
}
