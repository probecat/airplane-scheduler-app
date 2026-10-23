package io.github.probecat.airplanescheduler.core

object ConnectivityPlan {
    const val AIRPLANE = 1
    const val WIFI = 2

    data class Changes(val airplane: Boolean, val wifi: Boolean) {
        val isEmpty: Boolean get() = !airplane && !wifi

        val bits: Int get() = (if (airplane) AIRPLANE else 0) or (if (wifi) WIFI else 0)

        operator fun plus(other: Changes) = Changes(airplane || other.airplane, wifi || other.wifi)

        operator fun minus(other: Changes) = Changes(airplane && !other.airplane, wifi && !other.wifi)

        companion object {
            val NONE = Changes(airplane = false, wifi = false)

            fun of(bits: Int) = Changes(airplane = bits and AIRPLANE != 0, wifi = bits and WIFI != 0)
        }
    }

    fun start(airplaneOn: Boolean, wifiOn: Boolean, disableWifi: Boolean) =
        Changes(airplane = !airplaneOn, wifi = disableWifi && wifiOn)

    fun already(airplaneOn: Boolean, wifiOn: Boolean, disableWifi: Boolean) =
        Changes(airplane = airplaneOn, wifi = disableWifi && !wifiOn)

    fun end(started: Changes, enableWifi: Boolean) = started.copy(wifi = started.wifi && enableWifi)

    fun order(enabled: Boolean, changes: Changes): List<Int> {
        val airplane = AIRPLANE.takeIf { changes.airplane }
        val wifi = WIFI.takeIf { changes.wifi }
        return if (enabled) listOfNotNull(wifi, airplane) else listOfNotNull(airplane, wifi)
    }
}
