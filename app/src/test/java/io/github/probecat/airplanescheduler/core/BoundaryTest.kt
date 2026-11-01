package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.Boundary.Started
import io.github.probecat.airplanescheduler.core.Boundary.Step
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.ScheduleResolver.Window
import io.github.probecat.airplanescheduler.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundaryTest {
    private val morning = Schedule(id = 2, start = at(7), end = at(9))
    private val both = Changes(airplane = true, wifi = true)
    private val wifiOnly = Changes(airplane = false, wifi = true)

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
            Step.End(Changes(airplane = true, wifi = false), off, friday(7).plusDays(1)),
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
            Step.End(Changes(airplane = true, wifi = false), night.copy(enableWifi = false), friday(7).plusDays(1)),
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
            Step.End(Changes.NONE, night, friday(7).plusDays(1)),
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
        assertEquals(
            Step.End(both, ended, friday(7)),
            Boundary.atEdit(listOf(night), listOf(ended), friday(3), started),
        )
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
        assertEquals(night, Boundary.missed(listOf(night), friday(3), started = null, airplaneOn = false))
        assertEquals(night, Boundary.missed(listOf(night), friday(3), Started(thursdayNight, Changes.NONE), false))
        assertNull(Boundary.missed(listOf(night), friday(3), Started(thursdayNight, both), false))
        val wasOn = Started(thursdayNight, Changes.NONE, Changes.NONE, already = Changes(airplane = true, wifi = false))
        assertNull(Boundary.missed(listOf(night), friday(3), wasOn, false))
        assertNull(Boundary.missed(listOf(night), friday(3), started = null, airplaneOn = true))
        assertNull(Boundary.missed(listOf(night), friday(12), started = null, airplaneOn = false))
    }

    @Test
    fun aMissedStartInTouchingWindowsIsTheRunningSchedule() {
        assertEquals(morning, Boundary.missed(listOf(night, morning), friday(8), started = null, airplaneOn = false))
    }

    @Test
    fun aStartIsNotMissedWhileItSwitchesOrBeforeItsAlarmHadAMinute() {
        val start = friday(23)
        assertNull(Boundary.missed(listOf(night), start, started = null, airplaneOn = false))
        assertEquals(night, Boundary.missed(listOf(night), start.plusMinutes(1), started = null, airplaneOn = false))
        assertEquals(night, Boundary.missed(listOf(night), start, Started(start, Changes.NONE), false))
        assertNull(Boundary.missed(listOf(night), start, Started(start, Changes.NONE), false, switching = true))
    }

    @Test
    fun aStartSwitchesOnlyWhatIsNotAlreadyInPlace() {
        val window = Window(friday(23), inFirst = true, disableWifi = true)
        assertEquals(
            Started(friday(23), Changes.NONE, wanted = both) to both,
            Boundary.begin(window, started = null, airplaneOn = false, wifiOn = true, wifiKept = false),
        )
        val airplaneOnly = Changes(airplane = true, wifi = false)
        assertEquals(
            Started(friday(23), Changes.NONE, wanted = airplaneOnly, already = wifiOnly) to airplaneOnly,
            Boundary.begin(window, started = null, airplaneOn = false, wifiOn = false, wifiKept = false),
        )
        assertEquals(
            Started(friday(23), Changes.NONE, wanted = wifiOnly, already = airplaneOnly) to wifiOnly,
            Boundary.begin(window, started = null, airplaneOn = true, wifiOn = true, wifiKept = false),
        )
    }

    @Test
    fun aStartLeavesWifiAloneWhenTheWindowOrAnEarlierStartKeepsItOn() {
        val airplaneOnly = Changes(airplane = true, wifi = false)
        val keepsWifi = Window(friday(23), inFirst = true, disableWifi = false)
        assertEquals(airplaneOnly, Boundary.begin(keepsWifi, null, airplaneOn = false, wifiOn = true, false).second)
        val window = keepsWifi.copy(disableWifi = true)
        assertEquals(airplaneOnly, Boundary.begin(window, null, airplaneOn = false, wifiOn = true, true).second)
    }

    @Test
    fun aStartCaughtUpByHandKeepsWhatTheMissedOneChangedAndFound() {
        val window = Window(thursdayNight, inFirst = true, disableWifi = true)
        val missed = Started(thursdayNight, wifiOnly, wanted = both, already = Changes.NONE)
        assertEquals(
            missed to Changes(airplane = true, wifi = false),
            Boundary.begin(window, missed, airplaneOn = false, wifiOn = false, wifiKept = false),
        )
        // One from another window is replaced.
        val stale = Started(thursdayNight.minusDays(1), both)
        assertEquals(
            Started(thursdayNight, Changes.NONE, wanted = both),
            Boundary.begin(window, stale, airplaneOn = false, wifiOn = true, wifiKept = false).first,
        )
    }

    @Test
    fun aForgottenWindowReportsOnlyWhatItLeftSwitched() {
        val started = Started(thursdayNight, both)
        assertEquals(both, Boundary.leftSwitched(started, enableWifi = true, airplaneOn = true, wifiOn = false))
        assertEquals(
            Changes(airplane = true, wifi = false),
            Boundary.leftSwitched(started, enableWifi = false, airplaneOn = true, wifiOn = false),
        )
        assertEquals(Changes.NONE, Boundary.leftSwitched(started, enableWifi = true, airplaneOn = false, wifiOn = true))
        val untouched = Started(thursdayNight, Changes.NONE, wanted = both)
        assertEquals(
            Changes.NONE,
            Boundary.leftSwitched(untouched, enableWifi = true, airplaneOn = true, wifiOn = false),
        )
    }

    @Test
    fun pausingEndsTheRunningWindowAndItStartsAgainLater() {
        val started = Started(friday(23), both)
        val paused = ScheduleResolver.postpone(night, friday(23, 40), 30)
        val resumes = friday(23, 40).plusMinutes(30)
        assertEquals(
            Step.End(both, paused, friday(7).plusDays(1)),
            Boundary.atEdit(listOf(night), listOf(paused), friday(23, 40), started),
        )
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

    @Test
    fun wifiTurnedOnAgainstTheStartStaysOnUntilTheWindowEnds() {
        val until = friday(7).plusDays(1)
        val turnedOff = Started(friday(23), both)
        assertEquals(until, Boundary.keptWifi(turnedOff, wifiOn = true, until, kept = false))
        val wasOff = Started(friday(23), Changes(airplane = true, wifi = false), already = wifiOnly)
        assertEquals(until, Boundary.keptWifi(wasOff, wifiOn = true, until, kept = false))
        val restarted = Started(friday(23, 30), Changes(airplane = true, wifi = false))
        assertEquals(until, Boundary.keptWifi(restarted, wifiOn = true, until, kept = true))
    }

    @Test
    fun wifiIsNotKeptWithoutSomeoneTurningItOnAgainstTheStart() {
        val until = friday(7).plusDays(1)
        val started = Started(friday(23), both)
        assertNull(Boundary.keptWifi(started, wifiOn = false, until, kept = true))
        assertNull(Boundary.keptWifi(started, wifiOn = true, until = null, kept = false))
        assertNull(Boundary.keptWifi(started = null, wifiOn = true, until, kept = true))
        // The app couldn't turn Wi-Fi off.
        val untouched = Started(friday(23), Changes(airplane = true, wifi = false), wanted = both)
        assertNull(Boundary.keptWifi(untouched, wifiOn = true, until, kept = false))
    }
}
