package io.github.probecat.airplanescheduler.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.R
import io.github.probecat.airplanescheduler.system.Access
import io.github.probecat.airplanescheduler.system.AccessState
import io.github.probecat.airplanescheduler.system.ShizukuStatus

@Composable
fun AccessDialog(access: Access, onHelp: (() -> Unit)?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shizuku = ShizukuStatus.launchIntent(context)
    val (title, text) = when {
        access.ready -> return
        access.kind == Access.Kind.ROOT -> R.string.root_permission_title to R.string.root_permission_text
        // Sui has no app to open, so the dialog only says where its settings are.
        access.kind == Access.Kind.SUI -> R.string.sui_permission_title to R.string.sui_permission_text
        shizuku == null -> R.string.shizuku_missing_title to R.string.shizuku_missing_text
        access.state == AccessState.OFFLINE -> R.string.shizuku_offline_title to R.string.shizuku_offline_text
        else -> R.string.shizuku_permission_title to R.string.shizuku_permission_text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            if (access.kind == Access.Kind.SHIZUKU) {
                TextButton(onClick = {
                    onDismiss()
                    context.startActivity(shizuku ?: ShizukuStatus.repository())
                }) { Text(stringResource(if (shizuku != null) R.string.shizuku_open else R.string.shizuku_get)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = onHelp?.let { { TextButton(onClick = it) { Text(stringResource(R.string.help)) } } },
    )
}

@Composable
fun rememberAccessRequest(viewModel: AppViewModel, onHelp: (() -> Unit)? = null): (Access) -> Unit {
    val current by viewModel.access.collectAsStateWithLifecycle()
    // Only the state is saved, because the kind follows the setting.
    var explaining by rememberSaveable { mutableStateOf<AccessState?>(null) }
    explaining?.let { state ->
        AccessDialog(
            current.copy(state = state),
            onHelp = onHelp?.let { help ->
                {
                    explaining = null
                    help()
                }
            },
            onDismiss = { explaining = null },
        )
    }
    return { access ->
        when (access.state) {
            AccessState.READY -> {}
            AccessState.PERMISSION_NEEDED -> viewModel.requestAccess { explaining = access.state }
            AccessState.OFFLINE -> explaining = access.state
        }
    }
}

val AccessState.icon: Int
    @DrawableRes get() = when (this) {
        AccessState.READY -> R.drawable.ic_check_circle
        AccessState.OFFLINE -> R.drawable.ic_error
        AccessState.PERMISSION_NEEDED -> R.drawable.ic_lock
    }
