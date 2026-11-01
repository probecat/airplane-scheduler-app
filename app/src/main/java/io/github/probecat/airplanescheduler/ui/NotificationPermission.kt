package io.github.probecat.airplanescheduler.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
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

// Asks for notifications, or opens their settings when Android no longer shows its dialog, as after
// repeated denials or before Android 13. Without [fallback], only Android's dialog asks.
@SuppressLint("InlinedApi") // Asks only on Android 13 and later.
@Composable
fun rememberNotificationRequest(
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (fallback) context.startActivity(notificationSettings(context))
        } else {
            rationaleBefore = rationale()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
