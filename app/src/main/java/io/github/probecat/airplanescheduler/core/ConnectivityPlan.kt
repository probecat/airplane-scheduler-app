package io.github.probecat.airplanescheduler.core

object ConnectivityPlan {
    const val AIRPLANE = 1
    const val WIFI = 2

    private val WIFI_OFF = listOf("wifi", "set-wifi-enabled", "disabled")
    private val AIRPLANE_ON = listOf("connectivity", "airplane-mode", "enable")
    private val AIRPLANE_OFF = listOf("connectivity", "airplane-mode", "disable")
    private val WIFI_ON = listOf("wifi", "set-wifi-enabled", "enabled")

    // What the app changes at a start, so the end undoes only that.
    data class Changes(val airplane: Boolean, val wifi: Boolean) {
        val isEmpty: Boolean get() = !airplane && !wifi

        val bits: Int get() = (if (airplane) AIRPLANE else 0) or (if (wifi) WIFI else 0)

        operator fun plus(other: Changes) = Changes(airplane || other.airplane, wifi || other.wifi)

        companion object {
            val NONE = Changes(airplane = false, wifi = false)

            fun of(bits: Int) = Changes(airplane = bits and AIRPLANE != 0, wifi = bits and WIFI != 0)
        }
    }

    // A start leaves alone what the user already switched: airplane mode that is on, Wi-Fi that is off.
    fun start(airplaneOn: Boolean, wifiOn: Boolean, disableWifi: Boolean) =
        Changes(airplane = !airplaneOn, wifi = disableWifi && wifiOn)

    // What a start finds already as it wants it.
    fun already(airplaneOn: Boolean, wifiOn: Boolean, disableWifi: Boolean) =
        Changes(airplane = airplaneOn, wifi = disableWifi && !wifiOn)

    // An end undoes the start's changes, and turns Wi-Fi back on only when the schedule asks for it.
    fun end(started: Changes, enableWifi: Boolean) = started.copy(wifi = started.wifi && enableWifi)

    // Wi-Fi goes off before airplane mode turns on, and back on after it turns off.
    fun commands(enabled: Boolean, changes: Changes): List<Pair<Int, List<String>>> {
        val airplane = (AIRPLANE to if (enabled) AIRPLANE_ON else AIRPLANE_OFF).takeIf { changes.airplane }
        val wifi = (WIFI to if (enabled) WIFI_OFF else WIFI_ON).takeIf { changes.wifi }
        return if (enabled) listOfNotNull(wifi, airplane) else listOfNotNull(airplane, wifi)
    }
}
