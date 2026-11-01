package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ScheduleStatus.Action
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleStatusTest {
    private val today = friday(0).toLocalDate()
    private val saturday = friday(7).plusDays(1)

    private fun status(schedule: Schedule, now: LocalDateTime, missed: Boolean = false) =
        ScheduleStatus.of(schedule, listOf(schedule), now, missed)

    @Test
    fun upcomingCanBeSkippedAndPostponedOnceItIsSoon() {
        val far = status(night, friday(12)) as ScheduleStatus.Upcoming
        assertFalse(far.soon)
        assertEquals(emptySet<Action>(), far.actions)
        assertEquals(
            ScheduleStatus.Upcoming(today, friday(23), postponed = false, soon = true),
            status(night, friday(22, 30)),
        )
        assertEquals(setOf(Action.SKIP, Action.POSTPONE), status(night, friday(22, 30))?.actions)
    }

    @Test
    fun aPostponedWindowCanOnlyBeRestored() {
        val postponed = ScheduleResolver.postpone(night, friday(22, 30), 30)
        assertEquals(
            ScheduleStatus.Upcoming(today, friday(23, 30), postponed = true, soon = true),
            status(postponed, friday(22, 40)),
        )
        assertEquals(setOf(Action.SKIP, Action.UNDO), status(postponed, friday(22, 40))?.actions)
        assertEquals(ScheduleStatus.Paused(today, friday(23, 30)), status(postponed, friday(23, 10)))
    }

    @Test
    fun aRunningWindowEndsOrPausesAndAMissedOneSkipsOrTurnsOn() {
        val end = saturday
        assertEquals(ScheduleStatus.Running(today, end, missed = false), status(night, friday(23)))
        assertEquals(setOf(Action.SKIP, Action.POSTPONE), status(night, friday(23))?.actions)
        assertEquals(setOf(Action.SKIP, Action.TURN_ON), status(night, friday(23, 5), missed = true)?.actions)
    }

    @Test
    fun aSkipNotesWhetherItsWindowRanOrIsUnderWay() {
        val skipped = ScheduleResolver.skip(night, friday(22, 30))
        assertEquals(
            ScheduleStatus.Skipped(today, saturday, endedEarly = false, underway = false, soon = true),
            status(skipped, friday(22, 30)),
        )
        assertEquals(
            ScheduleStatus.Skipped(today, saturday, endedEarly = false, underway = true, soon = true),
            status(skipped, friday(23, 30)),
        )
        val ended = ScheduleResolver.skip(night, friday(23, 30), running = true)
        assertTrue((status(ended, friday(23, 30)) as ScheduleStatus.Skipped).endedEarly)
        assertEquals(setOf(Action.UNDO), status(ended, friday(23, 30))?.actions)
        assertNull(status(night.copy(enabled = false), friday(23)))
    }

    @Test
    fun aTapOfferedBeforeTheSameTapOnTheNotificationNoLongerApplies() {
        val now = friday(22, 30)
        val offered = status(night, now)!!
        val postponed = ScheduleResolver.postpone(night, now, 30)
        assertNotEquals(offered, status(postponed, now))
        assertFalse(status(postponed, now)!!.allows(Action.POSTPONE, today))
        val skipped = ScheduleResolver.skip(night, now)
        assertNotEquals(offered, status(skipped, now))
        assertFalse(status(skipped, now)!!.allows(Action.SKIP, today))
    }

    @Test
    fun aTapAppliesOnlyToItsWindowsDate() {
        val running = status(night, friday(23))!!
        assertTrue(running.allows(Action.SKIP, today))
        assertFalse(running.allows(Action.SKIP, today.minusDays(1)))
    }
}
