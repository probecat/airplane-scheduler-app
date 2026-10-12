package io.github.probecat.airplanescheduler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.data.Settings
import io.github.probecat.airplanescheduler.mock.Mock
import io.github.probecat.airplanescheduler.system.AlarmScheduler
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.ScheduleUpdater
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App

    val schedules: StateFlow<List<Schedule>> = app.schedules.schedules
        .map(::sorted)
        .stateIn(viewModelScope, SharingStarted.Eagerly, sorted(app.schedules.all))

    val settings: StateFlow<Settings> = app.settings.settings

    val shizuku: StateFlow<ShizukuState> = app.shizuku

    // Ticks on each minute so the active schedule and its time left follow the clock, and when debug builds
    // move the mock clock.
    val now: StateFlow<LocalDateTime> = combine(
        flow {
            while (true) {
                val now = LocalDateTime.now()
                emit(Unit)
                delay(60_000L - now.second * 1000L - now.nano / 1_000_000L + 50)
            }
        },
        Mock.config,
    ) { _, _ -> Mock.now() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Mock.now())

    fun refreshShizuku() = app.refreshShizuku()

    fun requestShizuku(): Boolean = ShizukuStatus.request()

    fun newSchedule(): Schedule = Schedule(
        id = app.schedules.nextId(),
        start = 23 * 60,
        end = 7 * 60,
        days = emptySet(),
        disableWifi = false,
        enableWifi = false,
    )

    fun draft(schedule: Schedule): Schedule = drafts[schedule.id]?.takeIf { it.first == schedule }?.second ?: schedule

    fun keepDraft(base: Schedule, draft: Schedule) {
        if (draft == base) drafts.remove(base.id) else drafts[base.id] = base to draft
    }

    fun clearDrafts() = drafts.clear()

    fun isNew(schedule: Schedule): Boolean = app.schedules.get(schedule.id) == null

    // Only enabled schedules are checked, so a disabled one may overlap anything, and so may one
    // that ran once and is over.
    fun conflict(schedule: Schedule): Schedule? {
        if (!schedule.enabled) return null
        val now = Mock.now()
        val others = app.schedules.all.filterNot { ScheduleResolver.hasEnded(it, now) }
        return ScheduleResolver.conflict(ScheduleResolver.dated(schedule, now), others)
    }

    // Returns the overlapping schedule instead of switching on. Switching on drops pending skips.
    fun setEnabled(schedule: Schedule, enabled: Boolean): Schedule? {
        val base = if (enabled) ScheduleResolver.unskip(schedule, Mock.now()) else schedule
        return save(base.copy(enabled = enabled))
    }

    // Skips the next window, or ends the running one early. A schedule that runs once is switched off
    // instead, since that is its only window.
    fun skip(schedule: Schedule) {
        if (schedule.once) setEnabled(schedule, false) else save(ScheduleResolver.skip(schedule, Mock.now()))
    }

    // Undoes [skip], which starts the window again if it's still running.
    fun unskip(id: Long): Schedule? {
        val schedule = app.schedules.get(id) ?: return null
        return if (schedule.once) setEnabled(schedule, true) else save(ScheduleResolver.unskip(schedule, Mock.now()))
    }

    fun save(edited: Schedule): Schedule? {
        val schedule = ScheduleResolver.dated(edited, Mock.now())
        conflict(schedule)?.let { return it }
        drafts.remove(schedule.id)
        val all = app.schedules.all
        val replaced = all.map { if (it.id == schedule.id) schedule else it }
        commit(if (all.any { it.id == schedule.id }) replaced else all + schedule)
        return null
    }

    fun delete(schedule: Schedule) {
        drafts.remove(schedule.id)
        commit(app.schedules.all.filterNot { it.id == schedule.id })
    }

    // Undoes a delete. It takes a new id if its old one was reused, and comes back switched off if it
    // now overlaps another schedule.
    fun restore(schedule: Schedule) {
        val id = if (app.schedules.get(schedule.id) == null) schedule.id else app.schedules.nextId()
        val restored = schedule.copy(id = id).let { if (conflict(it) != null) it.copy(enabled = false) else it }
        commit(app.schedules.all + restored)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val before = app.settings.current
        app.settings.update(transform)
        if (before.remindShizuku != app.settings.current.remindShizuku) {
            AlarmScheduler.scheduleAll(app)
        }
    }

    fun resetSettings() = updateSettings { Settings() }

    private fun commit(schedules: List<Schedule>) = ScheduleUpdater.update(app, schedules)

    private fun sorted(schedules: List<Schedule>) = schedules.sortedWith(compareBy({ it.start }, { it.end }, { it.id }))

    private companion object {
        // Unsaved sheet edits, kept until saved or the app closes. Each is dropped once the schedule it
        // started from changes.
        val drafts = mutableMapOf<Long, Pair<Schedule, Schedule>>()
    }
}
