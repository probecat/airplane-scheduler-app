package io.github.probecat.airplanescheduler.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus

@Composable
fun ShizukuDialog(state: ShizukuState, onHelp: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shizuku = ShizukuStatus.launchIntent(context)
    val (title, text) = when {
        state == ShizukuState.READY -> return
        shizuku == null ->
            "Shizuku isn't installed" to
                "Airplane Scheduler switches airplane mode through Shizuku. Install Shizuku and start it. Until " +
                "then, schedules can't switch anything."
        state == ShizukuState.OFFLINE ->
            "Shizuku isn't running" to
                "Airplane Scheduler switches airplane mode through Shizuku. Shizuku stops when the phone restarts, " +
                "so start it again in the Shizuku app. Until then, schedules can't switch anything."
        else ->
            "Allow access in Shizuku" to
                "Shizuku won't ask again. Open Shizuku, find Airplane Scheduler under authorized apps, and allow it."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.startActivity(shizuku ?: ShizukuStatus.repository())
            }) { Text(if (shizuku != null) "Open Shizuku" else "Get Shizuku") }
        },
        dismissButton = onHelp?.let { { TextButton(onClick = it) { Text("Help") } } },
    )
}
