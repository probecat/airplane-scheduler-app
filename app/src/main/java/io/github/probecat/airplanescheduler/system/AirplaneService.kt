package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.IAirplaneService
import io.github.probecat.airplanescheduler.core.ConnectivityPlan
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// Runs in the Shizuku process with shell privileges.
class AirplaneService : IAirplaneService.Stub() {
    override fun setEnabled(enabled: Boolean, airplane: Boolean, wifi: Boolean): Int =
        ConnectivityPlan.commands(enabled, ConnectivityPlan.Changes(airplane, wifi))
            .fold(0) { made, (change, command) -> if (run(command) == 0) made or change else made }

    override fun destroy() {
        exitProcess(0)
    }

    private fun run(arguments: List<String>): Int = try {
        val process = ProcessBuilder(listOf("/system/bin/cmd") + arguments).redirectErrorStream(true).start()
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
