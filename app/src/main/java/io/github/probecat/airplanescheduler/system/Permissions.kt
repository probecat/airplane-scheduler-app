package io.github.probecat.airplanescheduler.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import io.github.probecat.airplanescheduler.mock.Mock

fun notificationsAllowed(context: Context): Boolean = if (Mock.active) {
    Mock.config.value.notifications
} else {
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
