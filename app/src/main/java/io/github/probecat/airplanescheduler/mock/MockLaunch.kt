package io.github.probecat.airplanescheduler.mock

import android.content.Intent
import io.github.probecat.airplanescheduler.BuildConfig
import io.github.probecat.airplanescheduler.data.ThemeMode
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.ui.AppViewModel
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters

// Sets up a state from launch extras, so scripts can take screenshots without tapping through the app.
object MockLaunch {
    val SHIZUKU = mapOf(
        "ready" to ShizukuState.READY,
        "offline" to ShizukuState.OFFLINE,
        "no-access" to ShizukuState.PERMISSION_NEEDED,
    )

    const val USAGE = """adb shell am start -S \
  -n io.github.probecat.airplanescheduler.debug/io.github.probecat.airplanescheduler.ui.MainActivity \
  --es mock active --es theme dark

mock          scenario id, on or off
shizuku       ready, offline or no-access
shizukuApp    true or false
notifications true or false
battery       true or false
at            wed 23:15, 23:15 or real
clock         system, 12 or 24
locale        language tag or system
fontScale     number or system
animations    off, a scale such as 5, or system
theme         system, light, dark or black
dynamicColor  true or false
screen        schedules, settings, help, debug or mock

Any override turns mock data on."""

    fun handle(app: App, viewModel: AppViewModel, intent: Intent): Destination? {
        if (!BuildConfig.DEBUG) return null
        val extras = intent.extras ?: return null

        // Accepts --es, --ez and --ef alike.
        @Suppress("DEPRECATION")
        fun value(key: String): String? = extras.get(key)?.toString()

        fun flag(key: String): Boolean? = when (value(key)?.lowercase()) {
            "true", "on", "1" -> true
            "false", "off", "0" -> false
            else -> null
        }

        // "system" clears an option that otherwise follows the system.
        fun <T : Any> optional(key: String, current: T?, parse: (String) -> T?): T? {
            val text = value(key) ?: return current
            return if (text == "system") null else parse(text) ?: current
        }

        var destination: Destination? = null
        when (val mock = value("mock")) {
            null -> {}
            "on", "off" -> Mock.update(app) { it.copy(enabled = mock == "on") }
            else -> scenarios.find { it.id == mock }?.let {
                it.apply(app, viewModel)
                destination = it.destination
            }
        }

        val before = Mock.config.value
        val after = before.copy(
            shizuku = value("shizuku")?.let { SHIZUKU[it] } ?: before.shizuku,
            shizukuInstalled = flag("shizukuApp") ?: before.shizukuInstalled,
            notifications = flag("notifications") ?: before.notifications,
            batteryOptimized = flag("battery") ?: before.batteryOptimized,
            clockOffset = value("at")?.let { at ->
                if (at == "real") 0 else parseAt(at, LocalDateTime.now())?.let(Mock::offsetTo)
            } ?: before.clockOffset,
            is24Hour = optional("clock", before.is24Hour) { mapOf("12" to false, "24" to true)[it] },
            locale = optional("locale", before.locale) { it },
            fontScale = optional("fontScale", before.fontScale) { it.toFloatOrNull() },
            animationScale = optional("animations", before.animationScale) {
                if (it == "off") 0f else it.toFloatOrNull()
            },
        )
        if (after != before && value("mock") != "off") Mock.update(app) { after.copy(enabled = true) }

        val theme = value("theme")?.let { name -> ThemeMode.entries.find { it.name.equals(name, ignoreCase = true) } }
        val dynamicColor = flag("dynamicColor")
        if (theme != null || dynamicColor != null) {
            viewModel.updateSettings {
                it.copy(theme = theme ?: it.theme, dynamicColor = dynamicColor ?: it.dynamicColor)
            }
        }

        value("screen")?.let { name -> Destination.entries.find { it.name.equals(name, ignoreCase = true) } }
            ?.let { destination = it }
        return destination
    }

    // "wed 23:15" is that day in the current week, starting Monday; "23:15" is today.
    private fun parseAt(text: String, real: LocalDateTime): LocalDateTime? {
        val parts = text.trim().split(Regex("\\s+"))
        val time = try {
            LocalTime.parse(parts.last().padStart(5, '0'))
        } catch (_: DateTimeParseException) {
            return null
        }
        val date = when (parts.size) {
            1 -> real.toLocalDate()
            2 -> {
                val day = DayOfWeek.entries.find { it.name.startsWith(parts[0].uppercase()) && parts[0].length >= 2 }
                    ?: return null
                real.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .plusDays(day.ordinal.toLong())
            }
            else -> return null
        }
        return date.atTime(time)
    }
}
