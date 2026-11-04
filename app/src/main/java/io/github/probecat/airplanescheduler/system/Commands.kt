package io.github.probecat.airplanescheduler.system

import android.os.Build
import io.github.probecat.airplanescheduler.core.ConnectivityPlan

// Both Shizuku and root switch with these shell commands.
object Commands {
    fun of(change: Int, enabled: Boolean): List<String> =
        if (change == ConnectivityPlan.AIRPLANE) airplane(enabled) else wifi(!enabled)

    private fun airplane(on: Boolean) =
        listOf("/system/bin/cmd", "connectivity", "airplane-mode", if (on) "enable" else "disable")

    // Before Android 11, cmd cannot switch Wi-Fi.
    private fun wifi(on: Boolean) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        listOf("/system/bin/cmd", "wifi", "set-wifi-enabled", if (on) "enabled" else "disabled")
    } else {
        listOf("/system/bin/svc", "wifi", if (on) "enable" else "disable")
    }
}
