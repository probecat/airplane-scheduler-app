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
    fun `skip or postpone only in hour before start`() {
        val phone = Phone(night, now = friday(21, 59))
        assertEquals(emptySet<Action>(), phone.status()!!.actions)
        phone.now = friday(22)
        assertEquals(setOf(Action.SKIP, Action.POSTPONE), phone.status()!!.actions)
    }

    @Test
    fun `skipped or postponed window has no notice until it runs`() {
        val phone = Phone(night, now = friday(22, 30))
        phone.postpone()
        assertNull(Notice.of(phone.status(), phone.now, lead = 60))
        phone.passTo(friday(23, 30))
        assertTrue(Notice.of(phone.status(), phone.now, lead = 60) is Notice.Active)
        phone.skip()
        assertNull(Notice.of(phone.status(), phone.now, lead = 60))
    }

    @Test
    fun `skip leaves out next window only`() {
        val phone = Phone(night, now = friday(22, 30))
        assertTrue(phone.skip())
        phone.passTo(friday(23, 30))
        assertFalse(phone.airplaneOn)
        phone.passTo(saturday(23))
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `skip undoable until window would end`() {
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
    fun `postpone moves start only and only once`() {
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
    fun `no Postpone when nothing of window would remain`() {
        val short = night.copy(end = at(23, 20))
        assertNull(ScheduleResolver.postponedTo(short, friday(22, 30), 30))
        assertEquals(setOf(fridayDate), ScheduleResolver.postpone(short, friday(22, 30), 30).skipped)
    }

    @Test
    fun `zero postpone setting turns Postpone and Pause off`() {
        assertNull(ScheduleResolver.postponedTo(night, friday(22, 30), 0))
    }

    @Test
    fun `pause counts from now and keeps end`() {
        val phone = Phone(night, now = friday(22))
        phone.passTo(friday(23, 40))
        phone.postpone(minutes = 30)
        assertEquals(ScheduleStatus.Paused(fridayDate, saturday(0, 10)), phone.status())
        assertTrue(Notice.of(phone.status(), phone.now, lead = 60) is Notice.Paused)
        assertEquals(setOf(Action.SKIP, Action.UNDO), phone.status()!!.actions)
        assertEquals(saturday(7), ScheduleResolver.windowEnd(phone.schedule(), fridayDate))
    }

    @Test
    fun `running window ends early but missed one is skipped`() {
        // The app never started a missed window, so there is nothing to end or pause.
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
    fun `skipping once schedule switches it off`() {
        val phone = Phone(night.copy(days = emptySet(), date = fridayDate), now = friday(22, 30))
        phone.skip()
        assertFalse(phone.schedule().enabled)
        phone.passTo(friday(23))
        assertFalse(phone.airplaneOn)
    }

    @Test
    fun `stale dialog does not postpone after notification did`() {
        val phone = Phone(night, now = friday(22, 30))
        val dialog = phone.status()
        assertTrue(phone.notificationTap(Action.POSTPONE, night.id, fridayDate))
        assertFalse(phone.postpone(offered = dialog))
        assertFalse(phone.skip(offered = dialog))
        assertEquals(mapOf(fridayDate to 30), phone.schedule().delayed)
    }

    @Test
    fun `stale dialog does not skip after notification did`() {
        val phone = Phone(night, now = friday(22, 30))
        val dialog = phone.status()
        assertTrue(phone.notificationTap(Action.SKIP, night.id, fridayDate))
        assertFalse(phone.skip(offered = dialog))
        assertEquals(setOf(fridayDate), phone.schedule().skipped)
    }

    @Test
    fun `notification tap acts only on own window and state`() {
        val phone = Phone(night, now = friday(22, 30))
        assertFalse(phone.notificationTap(Action.SKIP, night.id, fridayDate.minusDays(1)))
        phone.notificationTap(Action.POSTPONE, night.id, fridayDate)
        assertFalse(phone.notificationTap(Action.POSTPONE, night.id, fridayDate))
        assertEquals(mapOf(fridayDate to 30), phone.schedule().delayed)
    }

    @Test
    fun `sheet save keeps skips and delays from notification`() {
        val phone = Phone(night, now = friday(22, 30))
        val open = phone.schedule()
        phone.notificationTap(Action.SKIP, night.id, fridayDate)
        phone.save(open.copy(name = "Sleep"))
        assertEquals("Sleep", phone.schedule().name)
        assertEquals(setOf(fridayDate), phone.schedule().skipped)
    }

    @Test
    fun `new times drop delays`() {
        val phone = Phone(night, now = friday(22, 30))
        phone.postpone()
        phone.save(phone.schedule().copy(end = at(8)))
        assertTrue(phone.schedule().delayed.isEmpty())
        phone.passTo(friday(23))
        assertTrue(phone.airplaneOn)
    }

    @Test
    fun `restored schedule gets new id or turns off on overlap`() {
        val reused = Schedule(id = night.id, start = at(12), end = at(13))
        val restored = ScheduleEdits.restore(listOf(reused), night, newId = 5, friday(12)) as ScheduleEdits.Result.Saved
        assertEquals(5L, restored.schedules.last().id)
        assertTrue(restored.schedules.last().enabled)
        val overlapping = Schedule(id = 3, start = at(22), end = at(23, 30))
        val off = ScheduleEdits.restore(listOf(night), overlapping, newId = 5, friday(12)) as ScheduleEdits.Result.Saved
        assertFalse(off.schedules.last().enabled)
    }

    @Test
    fun `restore leaves out window under way until turned on`() {
        // The restored phone did not start it, so its end would have no start to undo.
        val lunch = Schedule(id = 2, start = at(12), end = at(13))
        val restored = ScheduleEdits.restored(listOf(night, lunch), friday(23, 30))
        val phone = Phone(*restored.toTypedArray(), now = friday(23, 30))
        assertEquals(lunch, phone.schedule(2))
        assertTrue((phone.status() as ScheduleStatus.Skipped).underway)
        assertTrue(phone.undo())
        assertTrue(phone.airplaneOn)
        phone.passTo(saturday(7))
        assertFalse(phone.airplaneOn)

        val once = night.copy(days = emptySet(), date = fridayDate)
        assertFalse(ScheduleEdits.restored(listOf(once), friday(23, 30)).single().enabled)
    }
}
