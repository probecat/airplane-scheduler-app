package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine.Device
import io.github.probecat.airplanescheduler.core.Engine.Effect
import io.github.probecat.airplanescheduler.core.Engine.Event
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineTest {
    private val both = Changes(airplane = true, wifi = true)
    private val airplaneOnly = Changes(airplane = true, wifi = false)
    private val saturday = friday(7).plusDays(1)

    // Runs events against a device that switches as told while [works], and keeps what else they did.
    private class Phone(var device: Device = Device(airplaneOn = false, wifiOn = true)) {
        var state = Engine.State()
        var works = true
        val effects = mutableListOf<Effect>()

        fun send(event: Event) {
            val result = Engine.on(state, event, device)
            state = result.state
            for (effect in result.effects) {
                if (effect is Effect.Switch) finish(effect) else effects += effect
            }
        }

        // Starts a switch that finishes only when the returned function runs.
        fun sendPending(event: Event): () -> Unit {
            val result = Engine.on(state, event, device)
            state = result.state
            val switch = result.effects.single() as Effect.Switch
            return { finish(switch) }
        }

        private fun finish(switch: Effect.Switch) {
            val made = if (works) switch.changes else Changes.NONE
            val on = switch.enabled
            device = Device(
                airplaneOn = if (made.airplane) on else device.airplaneOn,
                wifiOn = if (made.wifi) !on else device.wifiOn,
            )
            send(Event.Switched(switch, made))
        }
    }

    private fun alarm(now: LocalDateTime, schedules: List<Schedule> = listOf(night)) = Event.Alarm(schedules, now)

    @Test
    fun aWindowSwitchesOnAndBackAndReportsItsEnd() {
        val phone = Phone()
        phone.send(alarm(friday(23)))
        assertEquals(Device(airplaneOn = true, wifiOn = false), phone.device)
        assertEquals(Started(friday(23), both), phone.state.started)
        phone.send(alarm(saturday))
        assertEquals(Device(airplaneOn = false, wifiOn = true), phone.device)
        assertNull(phone.state.started)
        assertEquals(listOf(Effect.Report(night, Ending(Changes.NONE, wifiBackOn = true))), phone.effects)
    }

    @Test
    fun anEndWhileTheStartIsSwitchingTurnsBackWhatTheStartMade() {
        val phone = Phone()
        val startSwitched = phone.sendPending(alarm(friday(23)))
        val off = night.copy(enabled = false)
        phone.send(Event.Edit(listOf(night), listOf(off), friday(23)))
        assertNull(phone.state.started)
        startSwitched()
        assertEquals(Device(airplaneOn = false, wifiOn = true), phone.device)
        assertNull(phone.state.started)
    }

    @Test
    fun aMissedStartIsReportedAtTheEnd() {
        val phone = Phone()
        phone.works = false
        phone.send(alarm(friday(23)))
        assertEquals(Started(friday(23), Changes.NONE, wanted = both), phone.state.started)
        phone.works = true
        phone.send(alarm(saturday))
        assertEquals(listOf(Effect.Report(night, Ending(Changes.NONE, missedStart = true))), phone.effects)
    }

    @Test
    fun catchingUpKeepsTheMissedStartSoTheEndUndoesBoth() {
        val phone = Phone()
        phone.works = false
        phone.send(alarm(friday(23)))
        phone.works = true
        phone.send(Event.CatchUp(listOf(night), friday(23, 30)))
        assertEquals(Started(friday(23), both), phone.state.started)
        phone.send(alarm(saturday))
        assertEquals(Device(airplaneOn = false, wifiOn = true), phone.device)
        assertEquals(listOf(Effect.Report(night, Ending(Changes.NONE, wifiBackOn = true))), phone.effects)
    }

    @Test
    fun anEndThatFailsIsReportedEvenWhenAnEditCausedIt() {
        val phone = Phone()
        phone.send(alarm(friday(23)))
        phone.works = false
        val ended = ScheduleResolver.skip(night, friday(23, 30), running = true)
        phone.send(Event.Edit(listOf(night), listOf(ended), friday(23, 30)))
        val ending = Ending(both, airplaneStaysOn = true)
        assertEquals(listOf(Effect.Report(ended, ending)), phone.effects)
    }

    @Test
    fun anEndThatAnEditCausedIsQuietWhenItWorks() {
        val phone = Phone()
        phone.send(alarm(friday(23)))
        val ended = ScheduleResolver.skip(night, friday(23, 30), running = true)
        phone.send(Event.Edit(listOf(night), listOf(ended), friday(23, 30)))
        assertEquals(Device(airplaneOn = false, wifiOn = true), phone.device)
        assertEquals(emptyList<Effect>(), phone.effects)
    }

    @Test
    fun wifiTurnedOnAgainstTheStartStaysOnWhenTheWindowStartsAgain() {
        val phone = Phone()
        phone.send(alarm(friday(23)))
        phone.device = phone.device.copy(wifiOn = true)
        val ended = ScheduleResolver.skip(night, friday(23, 30), running = true)
        phone.send(Event.Edit(listOf(night), listOf(ended), friday(23, 30)))
        assertEquals(saturday, phone.state.wifiKeptUntil)
        val resumed = ScheduleResolver.unskip(ended, friday(23, 40))
        phone.send(Event.Edit(listOf(ended), listOf(resumed), friday(23, 40)))
        assertEquals(Device(airplaneOn = true, wifiOn = true), phone.device)
        assertEquals(airplaneOnly, phone.state.started?.changes)
    }

    @Test
    fun movingTheStartOfARunningWindowKeepsWhatItSwitched() {
        val phone = Phone()
        phone.send(alarm(friday(23)))
        val earlier = night.copy(start = at(22, 30))
        phone.send(Event.Edit(listOf(night), listOf(earlier), friday(23, 10)))
        assertEquals(Started(friday(22, 30), both), phone.state.started)
        assertEquals(Device(airplaneOn = true, wifiOn = false), phone.device)
    }

    @Test
    fun aResetForgetsAWindowWhoseEndPassedAndReportsWhatItLeft() {
        val phone = Phone(Device(airplaneOn = true, wifiOn = false))
        phone.state = Engine.State(Started(thursdayNight, both))
        phone.send(Event.Reset(listOf(night), friday(23, 30).minusDays(1)))
        assertEquals(Started(thursdayNight, both), phone.state.started)
        phone.send(Event.Reset(listOf(night), friday(12)))
        assertNull(phone.state.started)
        assertEquals(listOf(Effect.MissedEnd(night, both)), phone.effects)
        assertEquals(Device(airplaneOn = true, wifiOn = false), phone.device)
    }

    @Test
    fun anAlarmAfterAMissedEndForgetsTheWindowWithoutSwitching() {
        val phone = Phone(Device(airplaneOn = true, wifiOn = true))
        phone.state = Engine.State(Started(thursdayNight.minusDays(1), both))
        phone.send(alarm(friday(12)))
        assertNull(phone.state.started)
        assertEquals(listOf(Effect.MissedEnd(night, airplaneOnly)), phone.effects)
    }
}
