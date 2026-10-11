package io.github.probecat.airplanescheduler.ui.help

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.ui.AppViewModel
import io.github.probecat.airplanescheduler.ui.Format
import io.github.probecat.airplanescheduler.ui.ISSUES_URL
import io.github.probecat.airplanescheduler.ui.LinkRow
import io.github.probecat.airplanescheduler.ui.SectionHeader
import io.github.probecat.airplanescheduler.ui.SettingRow
import io.github.probecat.airplanescheduler.ui.ShizukuDialog
import io.github.probecat.airplanescheduler.ui.Subpage
import io.github.probecat.airplanescheduler.ui.rememberFormat

private fun schedules(format: Format) = listOf(
    "What happens at the start and end?" to
        "At the start, airplane mode turns on. At the end, the app undoes only what it changed: if airplane " +
        "mode was already on at the start, it stays on. If you turn airplane mode off during a schedule, the " +
        "app leaves it off.",
    "How do the Wi-Fi options work?" to
        "“Turn off Wi-Fi at start” turns Wi-Fi off before airplane mode turns on, unless it's already off. " +
        "“Turn on Wi-Fi at end” turns it back on, but only if the app turned it off.",
    "Which day does an overnight schedule belong to?" to
        "The day it starts. A Friday schedule from ${format.time(23 * 60)} to ${format.time(7 * 60)} ends on " +
        "Saturday morning.",
    "What does a schedule without days do?" to
        "It runs once, then switches itself off. It starts the next time its times come around, or right " +
        "away if it covers the current time.",
    "What happens if I change an active schedule?" to
        "Switching it off or deleting it ends it right away, and the app undoes what it changed, as at a " +
        "normal end. Switching on or adding a schedule that covers the current time starts it right away. " +
        "Changing the times works the same way.",
)

private val troubleshooting = listOf(
    "Why didn't airplane mode turn on or off?" to
        "Shizuku wasn't running at that time. Nothing changes then, and the app doesn't reconcile until the " +
        "next schedule.",
    "Why did Shizuku stop?" to
        "Shizuku stops whenever the phone restarts. You need to open it and start it again. To get a " +
        "warning, turn on “Remind me if Shizuku is off” in Settings. You'll get a notification if Shizuku " +
        "isn't ready an hour before a schedule starts.",
    "Why can't two schedules overlap?" to
        "Schedules that are switched on can't overlap. If one ends exactly when another starts, they work as " +
        "one, and airplane mode stays on between them. Wi-Fi follows the first one's start option and the " +
        "last one's end option.",
)

@Composable
fun HelpScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val format = rememberFormat()
    Subpage("Help", onBack) {
        SectionHeader("Set up Shizuku")
        Text(
            "Android doesn't let apps switch airplane mode, so Airplane Scheduler does it through Shizuku.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SetupSteps(viewModel)

        SectionHeader("How schedules work")
        schedules(format).forEach { (question, answer) -> Topic(question, answer) }

        SectionHeader("Troubleshooting")
        troubleshooting.forEach { (question, answer) -> Topic(question, answer) }
        LinkRow(R.drawable.ic_bug_report, "Still stuck? Report an issue", ISSUES_URL)
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
    Step(R.drawable.ic_counter_1, "Install Shizuku", if (installed) "Installed" else "Not installed", installed) {
        TextButton(onClick = { context.startActivity(ShizukuStatus.repository()) }) { Text("Get") }
    }
    Step(R.drawable.ic_counter_2, "Start Shizuku", if (running) "Running" else "Not running", running) {
        if (installed) TextButton(onClick = { app?.let(context::startActivity) }) { Text("Open") }
    }
    Step(R.drawable.ic_counter_3, "Grant permission", if (allowed) "Allowed" else "Not allowed", allowed) {
        if (running) {
            TextButton(onClick = { if (!viewModel.requestShizuku()) explaining = shizuku }) { Text("Allow") }
        }
    }

    explaining?.let { state -> ShizukuDialog(state, onHelp = null, onDismiss = { explaining = null }) }
}

@Composable
private fun Step(number: Int, title: String, status: String, done: Boolean, action: @Composable () -> Unit) {
    SettingRow(
        icon = if (done) R.drawable.ic_check_circle else number,
        title = title,
        text = status,
        trailing = if (done) null else action,
    )
}

@Composable
private fun Topic(question: String, answer: String) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f)
    ListItem(
        headlineContent = { Text(question) },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, Modifier.rotate(turn))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClickLabel = if (open) "Collapse" else "Expand") { open = !open }
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .padding(horizontal = 8.dp),
    )
    AnimatedVisibility(open) {
        Text(
            answer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        )
    }
}
