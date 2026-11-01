package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ScheduleEdits.Result
import io.github.probecat.airplanescheduler.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditsTest {
    private val today = friday(0).toLocalDate()
    private val evening = Schedule(id = 2, start = at(20), end = at(22))

    private fun saved(result: Result) = (result as Result.Saved).schedules

    @Test
    fun anEditThatOverlapsIsNotSaved() {
        val overlapping = evening.copy(end = at(23, 30))
        assertEquals(Result.Overlaps(night), ScheduleEdits.save(listOf(night, evening), overlapping, friday(12)))
    }

    @Test
    fun newTimesDropTheDelays() {
        val postponed = ScheduleResolver.postpone(night, friday(22, 30), 30)
        val now = friday(22, 40)
        val retimed = saved(ScheduleEdits.save(listOf(postponed), postponed.copy(end = at(8)), now)).single()
        assertEquals(emptyMap<Any, Any>(), retimed.delayed)
        val renamed = saved(ScheduleEdits.save(listOf(postponed), postponed.copy(name = "Sleep"), now)).single()
        assertEquals(postponed.delayed, renamed.delayed)
    }

    @Test
    fun theSheetKeepsWhatANotificationChangedWhileItWasOpen() {
        val skipped = ScheduleResolver.skip(night, friday(22, 30))
        val edited = saved(
            ScheduleEdits.saveSheet(listOf(skipped), night.copy(name = "Sleep"), friday(22, 40)),
        ).single()
        assertEquals("Sleep", edited.name)
        assertEquals(setOf(today), edited.skipped)
    }

    @Test
    fun skippingARunningWindowEndsItEarlyUnlessItsStartWasMissed() {
        val now = friday(23, 30)
        assertEquals(
            setOf(today),
            saved(ScheduleEdits.skip(listOf(night), night, now, missed = false)).single().endedEarly,
        )
        val missed = saved(ScheduleEdits.skip(listOf(night), night, now, missed = true)).single()
        assertEquals(setOf(today), missed.skipped)
        assertEquals(emptySet<Any>(), missed.endedEarly)
    }

    @Test
    fun skippingAScheduleThatRunsOnceSwitchesItOffAndUndoingSwitchesItOn() {
        val once = night.copy(days = emptySet(), date = today)
        val off = saved(ScheduleEdits.skip(listOf(once), once, friday(22, 30), missed = false)).single()
        assertFalse(off.enabled)
        assertTrue(saved(ScheduleEdits.unskip(listOf(off), off, friday(22, 40))).single().enabled)
    }

    @Test
    fun aRestoredScheduleTakesANewIdWhenItsOwnWasReusedAndComesBackOffWhenItOverlaps() {
        val reused = evening.copy(id = night.id)
        val restored = saved(ScheduleEdits.restore(listOf(reused), night, newId = 5, friday(12))).last()
        assertEquals(5L, restored.id)
        assertTrue(restored.enabled)
        val overlapping = Schedule(id = 3, start = at(22), end = at(23, 30))
        assertFalse(saved(ScheduleEdits.restore(listOf(night), overlapping, newId = 5, friday(12))).last().enabled)
    }
}
