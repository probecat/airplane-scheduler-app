package io.github.probecat.airplanescheduler.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus

@Composable
fun ShizukuDialog(state: ShizukuState, onHelp: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shizuku = ShizukuStatus.launchIntent(context)
    val sui = ShizukuStatus.sui
    val (title, text) = when {
        state == ShizukuState.READY -> return
        // Sui has no app to open, so the dialog only says where its settings are.
        sui -> R.string.sui_permission_title to R.string.sui_permission_text
        shizuku == null -> R.string.shizuku_missing_title to R.string.shizuku_missing_text
        state == ShizukuState.OFFLINE -> R.string.shizuku_offline_title to R.string.shizuku_offline_text
        else -> R.string.shizuku_permission_title to R.string.shizuku_permission_text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            if (sui) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            } else {
                TextButton(onClick = {
                    onDismiss()
                    context.startActivity(shizuku ?: ShizukuStatus.repository())
                }) { Text(stringResource(if (shizuku != null) R.string.shizuku_open else R.string.shizuku_get)) }
            }
        },
        dismissButton = onHelp?.let { { TextButton(onClick = it) { Text(stringResource(R.string.help)) } } },
    )
}
