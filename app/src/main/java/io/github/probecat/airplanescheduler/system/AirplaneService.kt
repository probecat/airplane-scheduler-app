package io.github.probecat.airplanescheduler.system

import android.os.Build
import io.github.probecat.airplanescheduler.IAirplaneService
import io.github.probecat.airplanescheduler.core.ConnectivityPlan
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// Runs in the Shizuku process with shell privileges.
class AirplaneService : IAirplaneService.Stub() {
    override fun setEnabled(enabled: Boolean, airplane: Boolean, wifi: Boolean): Int =
        ConnectivityPlan.order(enabled, ConnectivityPlan.Changes(airplane, wifi)).fold(0) { made, change ->
            val command = if (change == ConnectivityPlan.AIRPLANE) airplane(enabled) else wifi(!enabled)
            if (run(command) == 0) made or change else made
        }

    override fun destroy() {
        exitProcess(0)
    }

    private fun airplane(on: Boolean) =
        listOf("/system/bin/cmd", "connectivity", "airplane-mode", if (on) "enable" else "disable")

    // Before Android 11, cmd can't switch Wi-Fi.
    private fun wifi(on: Boolean) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        listOf("/system/bin/cmd", "wifi", "set-wifi-enabled", if (on) "enabled" else "disabled")
    } else {
        listOf("/system/bin/svc", "wifi", if (on) "enable" else "disable")
    }

    private fun run(command: List<String>): Int = try {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            -1
        } else {
            process.exitValue()
        }
    } catch (_: IOException) {
        -1
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        -1
    }
}
