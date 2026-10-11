package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.AIRPLANE
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.WIFI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityPlanTest {
    private val wifiOff = listOf("wifi", "set-wifi-enabled", "disabled")
    private val airplaneOn = listOf("connectivity", "airplane-mode", "enable")
    private val airplaneOff = listOf("connectivity", "airplane-mode", "disable")
    private val wifiOn = listOf("wifi", "set-wifi-enabled", "enabled")

    @Test
    fun startChangesOnlyWhatIsNotAlreadyInPlace() {
        assertEquals(
            Changes(airplane = true, wifi = true),
            ConnectivityPlan.start(false, wifiOn = true, disableWifi = true),
        )
        assertEquals(
            Changes(airplane = false, wifi = true),
            ConnectivityPlan.start(true, wifiOn = true, disableWifi = true),
        )
        assertEquals(
            Changes(airplane = true, wifi = false),
            ConnectivityPlan.start(false, wifiOn = false, disableWifi = true),
        )
        assertEquals(
            Changes(airplane = true, wifi = false),
            ConnectivityPlan.start(false, wifiOn = true, disableWifi = false),
        )
        assertTrue(ConnectivityPlan.start(true, wifiOn = false, disableWifi = true).isEmpty)
    }

    @Test
    fun endTurnsWifiBackOnOnlyWhenAsked() {
        val both = Changes(airplane = true, wifi = true)
        assertEquals(both, ConnectivityPlan.end(both, enableWifi = true))
        assertEquals(Changes(airplane = true, wifi = false), ConnectivityPlan.end(both, enableWifi = false))
        assertEquals(Changes.NONE, ConnectivityPlan.end(Changes.NONE, enableWifi = true))
    }

    @Test
    fun startDisablesWifiBeforeEnablingAirplaneMode() {
        assertEquals(
            listOf(WIFI to wifiOff, AIRPLANE to airplaneOn),
            ConnectivityPlan.commands(enabled = true, Changes(airplane = true, wifi = true)),
        )
    }

    @Test
    fun endDisablesAirplaneModeBeforeEnablingWifi() {
        assertEquals(
            listOf(AIRPLANE to airplaneOff, WIFI to wifiOn),
            ConnectivityPlan.commands(enabled = false, Changes(airplane = true, wifi = true)),
        )
    }

    @Test
    fun onlyTheChangesRun() {
        assertEquals(
            listOf(AIRPLANE to airplaneOn),
            ConnectivityPlan.commands(true, Changes(airplane = true, wifi = false)),
        )
        assertEquals(listOf(WIFI to wifiOn), ConnectivityPlan.commands(false, Changes(airplane = false, wifi = true)))
        assertTrue(ConnectivityPlan.commands(true, Changes.NONE).isEmpty())
    }

    @Test
    fun changesSurviveTheirBits() {
        for (airplane in listOf(false, true)) {
            for (wifi in listOf(false, true)) {
                val changes = Changes(airplane, wifi)
                assertEquals(changes, Changes.of(changes.bits))
            }
        }
    }
}
