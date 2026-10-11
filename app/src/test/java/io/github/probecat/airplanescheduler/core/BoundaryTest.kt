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

    // 2026-10-09 is a Friday.
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
        assertEquals(Step.End(both), Boundary.atAlarm(listOf(night), friday(7), Started(thursdayNight, both)))
        assertEquals(
            Step.End(Changes(airplane = true, wifi = false)),
            Boundary.atAlarm(listOf(night.copy(enableWifi = false)), friday(7), Started(thursdayNight, both)),
        )
        val wifiOnly = Changes(airplane = false, wifi = true)
        assertEquals(Step.End(wifiOnly), Boundary.atAlarm(listOf(night), friday(7), Started(thursdayNight, wifiOnly)))
    }

    @Test
    fun endOfTouchingWindowsUsesTheLastWindowsWifiOption() {
        val schedules = listOf(night.copy(enableWifi = false), morning.copy(enableWifi = true))
        assertEquals(Step.End(both), Boundary.atAlarm(schedules, friday(9), Started(thursdayNight, both)))
    }

    @Test
    fun aWindowWhoseEndWasMissedIsForgottenNotUndone() {
        val stale = Started(thursdayNight.minusDays(1), both)
        assertEquals(Step.End(Changes.NONE), Boundary.atAlarm(listOf(night), friday(7), stale))
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
        assertEquals(
            Step.End(Changes(airplane = true, wifi = false)),
            Boundary.atEdit(
                listOf(night),
                listOf(night.copy(enabled = false, enableWifi = false)),
                friday(23, 30),
                Started(friday(23), both),
            ),
        )
    }

    @Test
    fun deletingTheRunningWindowUsesItsLastWifiOption() {
        assertEquals(
            Step.End(Changes(airplane = true, wifi = false)),
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
            Step.End(Changes.NONE),
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
}
