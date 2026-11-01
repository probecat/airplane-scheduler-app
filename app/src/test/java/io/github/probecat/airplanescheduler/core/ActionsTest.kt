package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ScheduleStatus.Action
import io.github.probecat.airplanescheduler.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// What Skip, Postpone, Pause, Undo, and Turn on do, and when the card and the notification offer them.
class ActionsTest {
    @Test
    fun `a window can be skipped or postponed only in the hour before it starts`() {
        // Its notification also shows at that hour.
        val phone = Phone(night, now = friday(21, 59))
        assertEquals(emptySet<Action>(), phone.status()!!.actions)
        assertNull(Notice.of(phone.status()))
        phone.now = friday(22)
        assertEquals(setOf(Action.SKIP, Action.POSTPONE), phone.status()!!.actions)
        assertTrue(Notice.of(phone.status()) is Notice.Upcoming)
    }

    @Test
    fun `skipping leaves out only the next window`() {
        val phone = Phone(night, now = friday(22, 30))
        assertTrue(phone.skip())
        phone.passTo(friday(23, 30))
        assertFalse(phone.airplaneOn)
        phone.passTo(saturday(23))
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `a skip can be undone until the window would have ended`() {
        // Undo after the planned start turns airplane mode on immediately.
        val phone = Phone(night, now = friday(22, 30))
        phone.skip()
        phone.passTo(friday(23, 30))
        val skipped = phone.status() as ScheduleStatus.Skipped
        assertTrue(skipped.underway)
        assertTrue(phone.undo())
        assertTrue(phone.airplaneOn)
        phone.skip()
        phone.passTo(saturday(7))
        assertTrue(phone.status() !is ScheduleStatus.Skipped)
    }

    @Test
    fun `postponing moves only the start, and only once`() {
        // The window still ends on time. After one postpone, the card and the notification offer only Undo and Skip.
        val phone = Phone(night, now = friday(22, 30))
        assertTrue(phone.postpone(minutes = 30))
        assertEquals(setOf(Action.SKIP, Action.UNDO), phone.status()!!.actions)
        assertFalse(phone.postpone(minutes = 30, offered = phone.status()))
        phone.passTo(friday(23, 29))
        assertFalse(phone.airplaneOn)
        phone.passTo(friday(23, 30))
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `Postpone isn't offered when it would leave nothing of the window`() {
        // A postpone past the end of a short window only skips it, so the card skips without a question.
        val short = night.copy(end = at(23, 20))
        assertNull(ScheduleResolver.postponedTo(short, friday(22, 30), 30))
        assertEquals(setOf(fridayDate), ScheduleResolver.postpone(short, friday(22, 30), 30).skipped)
    }

    @Test
    fun `a postpone setting of zero turns Postpone and Pause off`() {
        assertNull(ScheduleResolver.postponedTo(night, friday(22, 30), 0))
    }

    @Test
    fun `pausing counts from now and keeps the end`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 40))
        phone.postpone(minutes = 30)
        assertEquals(ScheduleStatus.Paused(fridayDate, saturday(0, 10)), phone.status())
        assertEquals(setOf(Action.SKIP, Action.UNDO), phone.status()!!.actions)
        assertEquals(saturday(7), ScheduleResolver.windowEnd(phone.schedule(), fridayDate))
    }

    @Test
    fun `a running window ends early, but one whose start was missed is skipped`() {
        // The app never started a missed window, so there is nothing to end or pause. The card offers Skip and Turn on,
        // and a later Undo does not count the window as ended early.
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 30))
        assertEquals(setOf(Action.SKIP, Action.POSTPONE), phone.status()!!.actions)
        phone.skip()
        assertTrue((phone.status() as ScheduleStatus.Skipped).endedEarly)

        val missed = Phone(night, now = friday(22))
        missed.shizukuWorks = false
        missed.passTo(friday(23, 30))
        assertEquals(setOf(Action.SKIP, Action.TURN_ON), missed.status()!!.actions)
        missed.skip()
        assertFalse((missed.status() as ScheduleStatus.Skipped).endedEarly)
    }

    @Test
    fun `skipping a schedule that runs once switches it off`() {
        // Its only window is gone, so it stays off and does not wait for a date that will not come.
        val phone = Phone(night.copy(days = emptySet(), date = fridayDate), now = friday(22, 30))
        phone.skip()
        assertFalse(phone.schedule().enabled)
        phone.passTo(friday(23))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `a dialog left open doesn't postpone again after the notification did`() {
        // The user opened the dialog on the card and then tapped Postpone on the notification. The choices in the
        // dialog were for the earlier state, so they do nothing.
        val phone = Phone(night, now = friday(22, 30))
        val dialog = phone.status()
        assertTrue(phone.notificationTap(Action.POSTPONE, night.id, fridayDate))
        assertFalse(phone.postpone(offered = dialog))
        assertFalse(phone.skip(offered = dialog))
        assertEquals(mapOf(fridayDate to 30), phone.schedule().delayed)
    }

    @Test
    fun `a dialog left open doesn't skip the following window after the notification skipped`() {
        val phone = Phone(night, now = friday(22, 30))
        val dialog = phone.status()
        assertTrue(phone.notificationTap(Action.SKIP, night.id, fridayDate))
        assertFalse(phone.skip(offered = dialog))
        assertEquals(setOf(fridayDate), phone.schedule().skipped)
    }

    @Test
    fun `a notification tap acts only on its own window and state`() {
        // A notification for the window of yesterday, or a notification with a used Postpone, does nothing.
        val phone = Phone(night, now = friday(22, 30))
        assertFalse(phone.notificationTap(Action.SKIP, night.id, fridayDate.minusDays(1)))
        phone.notificationTap(Action.POSTPONE, night.id, fridayDate)
        assertFalse(phone.notificationTap(Action.POSTPONE, night.id, fridayDate))
        assertEquals(mapOf(fridayDate to 30), phone.schedule().delayed)
    }

    @Test
    fun `saving the sheet keeps skips and delays a notification made while it was open`() {
        val phone = Phone(night, now = friday(22, 30))
        val open = phone.schedule()
        phone.notificationTap(Action.SKIP, night.id, fridayDate)
        phone.save(open.copy(name = "Sleep"))
        assertEquals("Sleep", phone.schedule().name)
        assertEquals(setOf(fridayDate), phone.schedule().skipped)
    }

    @Test
    fun `new times drop the delays, which were for the old ones`() {
        val phone = Phone(night, now = friday(22, 30))
        phone.postpone()
        phone.save(phone.schedule().copy(end = at(8)))
        assertTrue(phone.schedule().delayed.isEmpty())
        phone.passTo(friday(23))
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `a deleted schedule comes back with a new id if its own was reused, and off if it now overlaps`() {
        val reused = Schedule(id = night.id, start = at(12), end = at(13))
        val restored = ScheduleEdits.restore(listOf(reused), night, newId = 5, friday(12)) as ScheduleEdits.Result.Saved
        assertEquals(5L, restored.schedules.last().id)
        assertTrue(restored.schedules.last().enabled)
        val overlapping = Schedule(id = 3, start = at(22), end = at(23, 30))
        val off = ScheduleEdits.restore(listOf(night), overlapping, newId = 5, friday(12)) as ScheduleEdits.Result.Saved
        assertFalse(off.schedules.last().enabled)
    }
}
