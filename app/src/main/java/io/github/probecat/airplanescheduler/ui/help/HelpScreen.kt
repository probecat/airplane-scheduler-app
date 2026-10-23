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
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.batteryOptimizationDelays
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.ISSUES_URL
import io.github.probecat.airplanescheduler.ui.LinkRow
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.SettingRow
import io.github.probecat.airplanescheduler.ui.ShizukuDialog
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.rememberFormat
import io.github.probecat.airplanescheduler.ui.theme.LocalSuccessColor

@Composable
private fun topic(@StringRes question: Int, @StringRes answer: Int, vararg args: Any) =
    stringResource(question) to stringResource(answer, *args)

@Composable
private fun schedules(format: Format) = listOf(
    topic(R.string.help_start_end_q, R.string.help_start_end_a),
    topic(R.string.help_wifi_q, R.string.help_wifi_a),
    topic(R.string.help_overnight_q, R.string.help_overnight_a, format.time(23 * 60), format.time(7 * 60)),
    topic(R.string.help_once_q, R.string.help_once_a),
    topic(R.string.help_postpone_q, R.string.help_postpone_a),
    topic(R.string.help_change_active_q, R.string.help_change_active_a),
)

@Composable
private fun troubleshooting(format: Format, sui: Boolean): List<Pair<String, String>> {
    var shizukuStopped = stringResource(R.string.help_shizuku_stopped_a)
    // Wireless debugging, which Shizuku starts with, came in Android 11.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        shizukuStopped = format.sentences(shizukuStopped, stringResource(R.string.help_shizuku_stopped_legacy))
    }
    return listOfNotNull(
        topic(R.string.help_missed_q, R.string.help_missed_a),
        if (batteryOptimizationDelays) topic(R.string.help_late_q, R.string.help_late_a) else null,
        // Sui runs from boot, so these are about Shizuku only.
        if (sui) null else stringResource(R.string.help_shizuku_stopped_q) to shizukuStopped,
        if (sui) null else topic(R.string.help_root_q, R.string.help_root_a),
        topic(R.string.help_overlap_q, R.string.help_overlap_a),
    )
}

@Composable
fun HelpScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val format = rememberFormat()
    val sui by viewModel.sui.collectAsStateWithLifecycle()
    Subpage(stringResource(R.string.help), onBack) {
        SectionHeader(stringResource(if (sui) R.string.help_setup_sui else R.string.help_setup))
        Text(
            stringResource(if (sui) R.string.help_setup_text_sui else R.string.help_setup_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (sui) SuiSteps(viewModel) else SetupSteps(viewModel)

        SectionHeader(stringResource(R.string.help_schedules))
        schedules(format).forEach { (question, answer) -> Topic(question, answer) }

        SectionHeader(stringResource(R.string.help_troubleshooting))
        troubleshooting(format, sui).forEach { (question, answer) -> Topic(question, answer) }
        LinkRow(R.drawable.ic_bug_report, stringResource(R.string.help_report), ISSUES_URL)
    }
}

@Composable
private fun SetupSteps(viewModel: AppViewModel) {
    val context = LocalContext.current
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    var app by remember { mutableStateOf(ShizukuStatus.launchIntent(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { app = ShizukuStatus.launchIntent(context) }
    var explaining by rememberSaveable { mutableStateOf<ShizukuState?>(null) }

    val installed = app != null
    val running = installed && shizuku != ShizukuState.OFFLINE
    val allowed = shizuku == ShizukuState.READY
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
            TextButton(onClick = { if (!viewModel.requestShizuku()) explaining = shizuku }) {
                Text(stringResource(R.string.action_allow))
            }
        }
    }

    explaining?.let { state -> ShizukuDialog(state, onHelp = null, onDismiss = { explaining = null }) }
}

@Composable
private fun SuiSteps(viewModel: AppViewModel) {
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    var explaining by rememberSaveable { mutableStateOf<ShizukuState?>(null) }
    val allowed = shizuku == ShizukuState.READY
    Step(R.drawable.ic_counter_1, R.string.help_install_sui, R.string.help_installed, done = true) {}
    Step(
        R.drawable.ic_counter_2,
        R.string.help_grant,
        if (allowed) R.string.status_allowed else R.string.status_not_allowed,
        allowed,
    ) {
        TextButton(onClick = { if (!viewModel.requestShizuku()) explaining = shizuku }) {
            Text(stringResource(R.string.action_allow))
        }
    }

    explaining?.let { state -> ShizukuDialog(state, onHelp = null, onDismiss = { explaining = null }) }
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
        tint = if (done) LocalSuccessColor.current else MaterialTheme.colorScheme.error,
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
