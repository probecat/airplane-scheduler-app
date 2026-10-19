package io.github.probecat.airplanescheduler.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.probecat.airplanescheduler.mock.Mock
import io.github.probecat.airplanescheduler.system.App

// Asks for notifications, or opens their settings when Android no longer shows its dialog, as after
// repeated denials. Mock data acts as if they were allowed, since Android's dialog can't be mocked,
// unless [real] asks for the actual permission. Without [fallback], only Android's dialog asks.
@Composable
fun rememberNotificationRequest(
    real: Boolean = false,
    fallback: Boolean = true,
    onResult: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun rationale() = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (fallback && !granted && !rationaleBefore && !rationale()) {
            context.startActivity(notificationSettings(context))
        }
        onResult(granted)
    }
    return {
        if (Mock.active && !real) {
            Mock.update(App.from(context)) { it.copy(notifications = true) }
            onResult(true)
        } else {
            rationaleBefore = rationale()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
