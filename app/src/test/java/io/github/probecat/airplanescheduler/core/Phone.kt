package io.github.probecat.airplanescheduler.core

import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine.Effect
import io.github.probecat.airplanescheduler.core.Engine.Event
import io.github.probecat.airplanescheduler.core.ScheduleEdits.Result
import io.github.probecat.airplanescheduler.data.Schedule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

// 2026-10-09 is a Friday.
fun friday(hour: Int, minute: Int = 0): LocalDateTime = LocalDateTime.of(2026, 10, 9, hour, minute)

fun saturday(hour: Int, minute: Int = 0): LocalDateTime = friday(hour, minute).plusDays(1)

val fridayDate: LocalDate = friday(0).toLocalDate()

// Every night from 23:00 to 07:00. Wi-Fi goes off at the start and back on at the end.
val night = Schedule(id = 1, name = "Night", start = at(23), end = at(7))

// The rules of the app on a fake device. Alarms fire at the times that the app sets them. Taps go through the same
// edits and checks as the card and the notifications. While Shizuku works, it switches what the app tells it to.
class Phone(
    vararg schedules: Schedule,
    var now: LocalDateTime,
    var airplaneOn: Boolean = false,
    var wifiOn: Boolean = true,
) {
    var schedules = schedules.toList()
    var state = Engine.State()
    var shizukuWorks = true

    // While this is set, switches wait for [finishSwitches], as when Shizuku is slow to answer.
    var holdSwitches = false
    private val held = mutableListOf<Effect.Switch>()

    val reports = mutableListOf<Pair<Schedule, Ending>>()

    // What forgotten windows left switched. The app reports this and does not undo it.
    val leftSwitched = mutableListOf<Changes>()

    fun schedule(id: Long = schedules.first().id) = schedules.first { it.id == id }

    // Moves time forward to [until] and fires each start and end alarm on the way, as AlarmReceiver does.
    fun passTo(until: LocalDateTime) {
        while (true) {
            val next = ScheduleResolver.nextChange(schedules, now.atZone(ZoneOffset.UTC))?.toLocalDateTime()
            if (next == null || next.isAfter(until)) break
            now = next
            val event = Event.Alarm(schedules, now)
            schedules = ScheduleResolver.expire(schedules, now)
            send(event)
        }
        now = until
    }

    // The phone was off or the clock changed, so the alarms in between never came.
    fun rebootAt(time: LocalDateTime) {
        now = time
        send(Event.Reset(schedules, now))
    }

    fun missedStart(): Schedule? =
        Boundary.missed(schedules, now, state.started, airplaneOn, switching = held.isNotEmpty())

    fun status(id: Long = schedules.first().id): ScheduleStatus? =
        ScheduleStatus.of(schedule(id), schedules, now, missed = missedStart()?.id == id)

    // The card and the dialog offer taps only in some states. A tap acts only while the status is still the one it was
    // offered for.
    private fun offers(id: Long, offered: ScheduleStatus?, action: ScheduleStatus.Action) =
        offered != null && action in offered.actions && status(id) == offered

    fun skip(id: Long = schedules.first().id, offered: ScheduleStatus? = status(id)): Boolean {
        if (!offers(id, offered, ScheduleStatus.Action.SKIP)) return false
        val missed = (offered as? ScheduleStatus.Running)?.missed == true
        return commit(ScheduleEdits.skip(schedules, schedule(id), now, missed)) == null
    }

    fun postpone(id: Long = schedules.first().id, minutes: Int = 30, offered: ScheduleStatus? = status(id)): Boolean {
        if (!offers(id, offered, ScheduleStatus.Action.POSTPONE)) return false
        return commit(ScheduleEdits.postpone(schedules, schedule(id), now, minutes)) == null
    }

    fun undo(id: Long = schedules.first().id, offered: ScheduleStatus? = status(id)): Boolean {
        if (!offers(id, offered, ScheduleStatus.Action.UNDO)) return false
        return commit(ScheduleEdits.unskip(schedules, schedule(id), now)) == null
    }

    fun turnOn(id: Long = schedules.first().id): Boolean {
        val offered = status(id)
        if (offered !is ScheduleStatus.Running || !offered.missed) return false
        send(Event.CatchUp(schedules, now))
        return true
    }

    // A tap on a notification, for the window that started on [date].
    fun notificationTap(action: ScheduleStatus.Action, id: Long, date: LocalDate, minutes: Int = 30): Boolean {
        val status = status(id)
        if (status?.allows(action, date) != true) return false
        val missed = (status as? ScheduleStatus.Running)?.missed == true
        when (action) {
            ScheduleStatus.Action.SKIP -> commit(ScheduleEdits.skip(schedules, schedule(id), now, missed))
            ScheduleStatus.Action.POSTPONE -> commit(ScheduleEdits.postpone(schedules, schedule(id), now, minutes))
            ScheduleStatus.Action.UNDO -> commit(ScheduleEdits.unskip(schedules, schedule(id), now))
            ScheduleStatus.Action.TURN_ON -> send(Event.CatchUp(schedules, now))
        }
        return true
    }

    // Returns the schedule that it overlaps, and does not save.
    fun save(edited: Schedule): Schedule? = commit(ScheduleEdits.saveSheet(schedules, edited, now))

    fun setEnabled(id: Long, enabled: Boolean): Schedule? =
        commit(ScheduleEdits.setEnabled(schedules, schedule(id), enabled, now))

    fun delete(id: Long) = commit(ScheduleEdits.delete(schedules, id))

    // The Retry on the notification after an end failed to undo [changes].
    fun retry(changes: Changes): Ending {
        val made = if (shizukuWorks) changes else Changes.NONE
        apply(false, made)
        return Ending.retried(changes, made, airplaneOn)
    }

    fun finishSwitches() {
        holdSwitches = false
        while (held.isNotEmpty()) finish(held.removeAt(0))
    }

    private fun commit(result: Result): Schedule? = when (result) {
        is Result.Overlaps -> result.schedule
        is Result.Saved -> {
            val old = schedules
            schedules = result.schedules
            send(Event.Edit(old, schedules, now))
            null
        }
    }

    private fun send(event: Event) {
        val result = Engine.on(state, event, Engine.Device(airplaneOn, wifiOn))
        state = result.state
        for (effect in result.effects) {
            when (effect) {
                is Effect.Switch -> if (holdSwitches) held += effect else finish(effect)
                is Effect.Report -> reports += effect.schedule to effect.ending
                is Effect.MissedEnd -> leftSwitched += effect.left
            }
        }
    }

    private fun finish(switch: Effect.Switch) {
        val made = if (shizukuWorks) switch.changes else Changes.NONE
        apply(switch.enabled, made)
        send(Event.Switched(switch, made))
    }

    private fun apply(enabled: Boolean, made: Changes) {
        if (made.airplane) airplaneOn = enabled
        if (made.wifi) wifiOn = !enabled
    }
}
