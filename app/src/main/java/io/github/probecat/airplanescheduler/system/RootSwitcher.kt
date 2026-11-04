package io.github.probecat.airplanescheduler.system

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.core.ConnectivityPlan
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootSwitcher(
    context: Context,
    private val changed: () -> Unit,
    @VisibleForTesting private val shell: List<String> = listOf("su", "-c"),
) : Switcher {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Root managers can show a toast each time su runs, so access is not checked on its own. It is what the last
    // su call found.
    private var granted: Boolean
        get() = prefs.getBoolean(GRANTED, false)
        set(value) {
            if (value == granted) return
            prefs.edit { putBoolean(GRANTED, value) }
            changed()
        }

    override fun access() = Access(Access.Kind.ROOT, if (granted) AccessState.READY else AccessState.PERMISSION_NEEDED)

    override suspend fun request(): Boolean = su("true") != null

    override suspend fun connect(tap: Boolean) = true

    // One su call for all changes, so a switch shows at most one toast.
    override suspend fun set(enabled: Boolean, changes: Changes): Changes {
        if (changes.isEmpty) return Changes.NONE
        val script = ConnectivityPlan.order(enabled, changes).joinToString("; ") { change ->
            Commands.of(change, enabled).joinToString(" ") + " >/dev/null 2>&1; echo $change \$?"
        }
        val output = su(script) ?: return Changes.NONE
        return Changes.of(
            output.mapNotNull { REPORT.matchEntire(it)?.destructured }
                .filter { (_, code) -> code == "0" }
                .fold(0) { made, (change) -> made or change.toInt() },
        )
    }

    // Null when su is missing, denied, or the user did not answer its prompt in time.
    private suspend fun su(script: String): List<String>? {
        val output = withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(shell + script).redirectErrorStream(true).start()
                if (process.waitFor(20, TimeUnit.SECONDS)) {
                    process.inputStream.bufferedReader().readLines().takeIf { process.exitValue() == 0 }
                } else {
                    process.destroyForcibly()
                    null
                }
            } catch (_: IOException) {
                null
            } catch (_: InterruptedException) {
                null
            }
        }
        granted = output != null
        return output
    }

    private companion object {
        const val GRANTED = "rootGranted"
        val REPORT = Regex("""(\d+) (\d+)""")
    }
}
