package io.github.probecat.airplanescheduler.system

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine
import java.time.format.DateTimeFormatter
import java.util.Locale

// Wording for the event log and the debug info. Both stay in English and use the same terms as the code, so reports
// read the same for everyone.
object Events {
    val MOMENT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun access(access: Access): String = "${access.kind.name} ${access.state.name}"

    // As in "airplane | Wi-Fi".
    fun changes(changes: Changes): String =
        listOfNotNull("airplane".takeIf { changes.airplane }, "Wi-Fi".takeIf { changes.wifi })
            .joinToString(" | ").ifEmpty { "none" }

    // As in "airplane ON | Wi-Fi OFF FAILED".
    fun switched(enabled: Boolean, wanted: Changes, made: Changes): String {
        if (wanted.isEmpty) return "nothing to switch"
        return listOfNotNull(
            "airplane ${onOff(enabled)}${failed(made.airplane)}".takeIf { wanted.airplane },
            "Wi-Fi ${onOff(!enabled)}${failed(made.wifi)}".takeIf { wanted.wifi },
        ).joinToString(" | ")
    }

    fun then(then: Engine.Then): String = when (then) {
        Engine.Then.Start -> "START"
        is Engine.Then.End -> "END #${then.schedule.id}"
        Engine.Then.Undo -> "UNDO late start"
    }

    fun received(action: String?): String = "Received ${name(action)}"

    // The last part of an action, as in "BOOT_COMPLETED" or "SKIP".
    fun name(action: String?): String = action?.substringAfterLast('.') ?: "UNKNOWN"

    fun onOff(on: Boolean) = if (on) "ON" else "OFF"

    fun yesNo(yes: Boolean) = if (yes) "YES" else "NO"

    private fun failed(made: Boolean) = if (made) "" else " FAILED"
}
