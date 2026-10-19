package io.github.probecat.airplanescheduler.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import io.github.probecat.airplanescheduler.mock.Mock

// Before Android 13, notifications need no permission, but can be switched off for the app.
fun notificationsAllowed(context: Context): Boolean = when {
    Mock.active -> Mock.config.value.notifications
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    else -> NotificationManagerCompat.from(context).areNotificationsEnabled()
}

// Android 12 lets exact alarms be denied, and Android 13 grants them to this app for good.
val exactAlarmsDeniable = Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2

fun exactAlarmsAllowed(context: Context): Boolean =
    !exactAlarmsDeniable || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

@SuppressLint("InlinedApi") // Opened only where exact alarms can be denied.
fun exactAlarmSettings(context: Context): Intent =
    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
