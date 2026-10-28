package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.Boundary.Step
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleResolver.Window
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundaryTest {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun friday(hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 9, hour, minute)

    private val thursdayNight = friday(23).minusDays(1)
    private val night = Schedule(id = 1, start = at(23), end = at(7))
    private val morning = Schedule(id = 2, start = at(7), end = at(9))
    private val both = Changes(airplane = true, wifi = true)

    @Test
    fun alarmAtTheStartStartsTheWindow() {
        assertEquals(
            Step.Start(Window(friday(23), inFirst = true, disableWifi = true)),
            Boundary.atAlarm(listOf(night), friday(23), started = null),
        )
    }

    @Test
    fun aNewStartReplacesAWindowWhoseEndWasMissed() {
        val stale = Started(thursdayNight.minusDays(1), both)
        assertEquals(
            Step.Start(Window(friday(23), inFirst = true, disableWifi = true)),
            Boundary.atAlarm(listOf(night), friday(23), stale),
        )
    }

    @Test
    fun theStartedWindowDoesNotStartTwice() {
        assertEquals(Step.None, Boundary.atAlarm(listOf(night), friday(23, 1), Started(friday(23), both)))
    }

    @Test
    fun touchingWindowsContinueWithoutAStart() {
        val schedules = listOf(night, morning)
        assertEquals(Step.None, Boundary.atAlarm(schedules, friday(7), Started(thursdayNight, both)))
        // A missed start is not caught up at the boundary between the windows either.
        assertEquals(Step.None, Boundary.atAlarm(schedules, friday(7), started = null))
    }

    @Test
    fun endUndoesTheStartsChanges() {
        assertEquals(Step.End(both, night), Boundary.atAlarm(listOf(night), friday(7), Started(thursdayNight, both)))
        val keepsWifiOff = night.copy(enableWifi = false)
        assertEquals(
            Step.End(Changes(airplane = true, wifi = false), keepsWifiOff),
            Boundary.atAlarm(listOf(keepsWifiOff), friday(7), Started(thursdayNight, both)),
        )
        val wifiOnly = Changes(airplane = false, wifi = true)
        assertEquals(
            Step.End(wifiOnly, night),
            Boundary.atAlarm(listOf(night), friday(7), Started(thursdayNight, wifiOnly)),
        )
    }

    @Test
    fun endOfTouchingWindowsUsesTheLastWindowsWifiOption() {
        val schedules = listOf(night.copy(enableWifi = false), morning.copy(enableWifi = true))
        assertEquals(Step.End(both, schedules[1]), Boundary.atAlarm(schedules, friday(9), Started(thursdayNight, both)))
    }

    @Test
    fun aWindowWhoseEndWasMissedIsForgottenNotUndone() {
        val stale = Started(thursdayNight.minusDays(1), both)
        assertEquals(Step.End(Changes.NONE, null), Boundary.atAlarm(listOf(night), friday(7), stale))
    }

    @Test
    fun endWithoutAStartedWindowDoesNothing() {
        assertEquals(Step.None, Boundary.atAlarm(listOf(night), friday(7), started = null))
    }

    @Test
    fun switchingOnAWindowThatCoversNowStartsIt() {
        assertEquals(
            Step.Start(Window(friday(23), inFirst = true, disableWifi = true)),
            Boundary.atEdit(listOf(night.copy(enabled = false)), listOf(night), friday(23, 30), started = null),
        )
    }

    @Test
    fun switchingOffTheRunningWindowEndsIt() {
        // The edited schedule's Wi-Fi option wins over the saved one.
        val off = night.copy(enabled = false, enableWifi = false)
        assertEquals(
            Step.End(Changes(airplane = true, wifi = false), off),
            Boundary.atEdit(
                listOf(night),
                listOf(off),
                friday(23, 30),
                Started(friday(23), both),
            ),
        )
    }

    @Test
    fun deletingTheRunningWindowUsesItsLastWifiOption() {
        assertEquals(
            Step.End(Changes(airplane = true, wifi = false), night.copy(enableWifi = false)),
            Boundary.atEdit(
                listOf(night.copy(enableWifi = false)),
                emptyList(),
                friday(23, 30),
                Started(friday(23), both),
            ),
        )
    }

    @Test
    fun switchingOffAWindowWhoseStartWasMissedChangesNothing() {
        assertEquals(
            Step.End(Changes.NONE, night),
            Boundary.atEdit(listOf(night), emptyList(), friday(23, 30), started = null),
        )
    }

    @Test
    fun editingTheTimesOfTheRunningWindowMovesItsStart() {
        assertEquals(
            Step.Move(Started(friday(22), both)),
            Boundary.atEdit(
                listOf(night),
                listOf(night.copy(start = at(22))),
                friday(23, 30),
                Started(friday(23), both),
            ),
        )
    }

    @Test
    fun deletingTheFirstOfTouchingWindowsMovesTheStart() {
        assertEquals(
            Step.Move(Started(friday(7), both)),
            Boundary.atEdit(listOf(night, morning), listOf(morning), friday(8), Started(thursdayNight, both)),
        )
    }

    @Test
    fun editsThatLeaveTheWindowAloneChangeNothing() {
        val lunch = Schedule(id = 3, start = at(12), end = at(13))
        assertEquals(
            Step.None,
            Boundary.atEdit(listOf(night), listOf(night, lunch), friday(23, 30), Started(friday(23), both)),
        )
        assertEquals(Step.None, Boundary.atEdit(listOf(night), listOf(night, lunch), friday(14), started = null))
        // An edit doesn't catch up a missed start.
        assertEquals(
            Step.None,
            Boundary.atEdit(listOf(night), listOf(night.copy(start = at(22))), friday(23, 30), null),
        )
    }

    @Test
    fun endingEarlyEndsTheRunningWindow() {
        val started = Started(thursdayNight, both)
        val ended = ScheduleResolver.skip(night, friday(3))
        assertEquals(Step.End(both, ended), Boundary.atEdit(listOf(night), listOf(ended), friday(3), started))
    }

    @Test
    fun skippingTheNextOfTwoTouchingWindowsEndsAtTheFirstEnd() {
        val started = Started(thursdayNight, both)
        val skipped = listOf(night, ScheduleResolver.skip(morning, friday(3)))
        assertEquals(Step.None, Boundary.atEdit(listOf(night, morning), skipped, friday(3), started))
        assertEquals(Step.End(both, night), Boundary.atAlarm(skipped, friday(7), started))
    }

    @Test
    fun undoingAnEarlyEndStartsTheWindowAgain() {
        val ended = ScheduleResolver.skip(night, friday(3))
        assertEquals(
            Step.Start(Window(thursdayNight, inFirst = true, disableWifi = true)),
            Boundary.atEdit(listOf(ended), listOf(ScheduleResolver.unskip(ended, friday(3))), friday(3), null),
        )
    }

    @Test
    fun aStartThatDidNotTurnOnAirplaneModeIsMissed() {
        val window = Window(thursdayNight, inFirst = true, disableWifi = true)
        assertEquals(window, Boundary.missed(listOf(night), friday(3), started = null, airplaneOn = false))
        assertEquals(window, Boundary.missed(listOf(night), friday(3), Started(thursdayNight, Changes.NONE), false))
        assertEquals(null, Boundary.missed(listOf(night), friday(3), Started(thursdayNight, both), false))
        val wasOn = Started(thursdayNight, Changes.NONE, Changes.NONE, already = Changes(airplane = true, wifi = false))
        assertEquals(null, Boundary.missed(listOf(night), friday(3), wasOn, false))
        assertEquals(null, Boundary.missed(listOf(night), friday(3), started = null, airplaneOn = true))
        assertEquals(null, Boundary.missed(listOf(night), friday(12), started = null, airplaneOn = false))
    }

    @Test
    fun aStartIsNotMissedWhileItSwitchesOrBeforeItsAlarmHadAMinute() {
        val start = friday(23)
        val window = Window(start, inFirst = true, disableWifi = true)
        assertEquals(null, Boundary.missed(listOf(night), start, started = null, airplaneOn = false))
        assertEquals(window, Boundary.missed(listOf(night), start.plusMinutes(1), started = null, airplaneOn = false))
        assertEquals(window, Boundary.missed(listOf(night), start, Started(start, Changes.NONE), false))
        assertEquals(null, Boundary.missed(listOf(night), start, Started(start, Changes.NONE), false, switching = true))
    }

    @Test
    fun pausingEndsTheRunningWindowAndItStartsAgainLater() {
        val started = Started(friday(23), both)
        val paused = ScheduleResolver.postpone(night, friday(23, 40), 30)
        val resumes = friday(23, 40).plusMinutes(30)
        assertEquals(Step.End(both, paused), Boundary.atEdit(listOf(night), listOf(paused), friday(23, 40), started))
        assertEquals(Step.None, Boundary.atAlarm(listOf(paused), friday(23, 50), started = null))
        val restarted = Window(resumes, inFirst = true, disableWifi = true)
        assertEquals(Step.Start(restarted), Boundary.atAlarm(listOf(paused), resumes, started = null))
        assertEquals(
            Step.End(both, paused),
            Boundary.atAlarm(listOf(paused), friday(7).plusDays(1), Started(resumes, both)),
        )
    }

    @Test
    fun postponingBeforeTheStartChangesNothingYet() {
        val postponed = ScheduleResolver.postpone(night, friday(22, 30), 30)
        assertEquals(Step.None, Boundary.atEdit(listOf(night), listOf(postponed), friday(22, 30), started = null))
        assertEquals(Step.None, Boundary.atAlarm(listOf(postponed), friday(23), started = null))
    }
}
