package io.github.probecat.airplanescheduler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.data.Settings
import io.github.probecat.airplanescheduler.mock.Mock
import io.github.probecat.airplanescheduler.system.AirplaneController
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.Notifier
import io.github.probecat.airplanescheduler.system.ScheduleUpdater
import io.github.probecat.airplanescheduler.system.ShizukuState
import io.github.probecat.airplanescheduler.system.ShizukuStatus
import io.github.probecat.airplanescheduler.system.notificationsAllowed
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

    val sui: StateFlow<Boolean> = app.sui

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

    // The running window's schedule when the app missed its start.
    val missed: StateFlow<Long?> = combine(schedules, now, app.settings.startedWindow, app.airplane, ::missed)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Notices tell whether Shizuku is ready, which a fresh process may not know yet.
    fun refresh() {
        AirplaneController.awaitBinder(tap = true) {
            app.refreshShizuku()
            Notifier.refresh(app)
        }
    }

    fun catchUp(done: (Boolean) -> Unit) = ScheduleUpdater.catchUp(app, done)

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

    fun conflict(schedule: Schedule): Schedule? = ScheduleUpdater.conflict(app, schedule)

    // Returns the overlapping schedule instead of switching on.
    fun setEnabled(schedule: Schedule, enabled: Boolean): Schedule? =
        ScheduleUpdater.setEnabled(app, schedule, enabled).also { if (it == null) drafts.remove(schedule.id) }

    fun skip(schedule: Schedule) = ScheduleUpdater.skip(app, schedule)

    fun postpone(schedule: Schedule) = ScheduleUpdater.postpone(app, schedule, settings.value.postponeMinutes)

    fun unskip(id: Long): Schedule? = ScheduleUpdater.unskip(app, id)

    // The sheet doesn't edit whether a schedule is on, nor its skips and delays, which a notification may
    // have changed while it was open.
    fun save(edited: Schedule): Schedule? {
        val stored = app.schedules.get(edited.id)
        val schedule = stored?.let {
            edited.copy(enabled = it.enabled, skipped = it.skipped, endedEarly = it.endedEarly, delayed = it.delayed)
        } ?: edited
        return ScheduleUpdater.save(app, schedule).also { if (it == null) drafts.remove(edited.id) }
    }

    // True once, when the first schedule is saved and notifications aren't allowed yet.
    fun asksNotifications(): Boolean {
        if (app.settings.current.askedNotifications) return false
        updateSettings { it.copy(askedNotifications = true) }
        return !notificationsAllowed(app)
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
        if (before.postponeMinutes != app.settings.current.postponeMinutes) Notifier.refresh(app)
    }

    fun resetSettings() = updateSettings { Settings() }

    private fun commit(schedules: List<Schedule>) = ScheduleUpdater.update(app, schedules)

    // Mock data reads airplane mode as off, so only the started window decides.
    private fun missed(schedules: List<Schedule>, now: LocalDateTime, started: Boundary.Started?, airplane: Boolean) =
        Boundary.missed(schedules, now, started, airplane && !Mock.active, AirplaneController.switching)
            ?.let { ScheduleResolver.activeSchedule(schedules, now)?.id }

    private fun sorted(schedules: List<Schedule>) = schedules.sortedWith(compareBy({ it.start }, { it.end }, { it.id }))

    private companion object {
        // Unsaved sheet edits, kept until saved or the app closes. Each is dropped once the schedule it
        // started from changes.
        val drafts = mutableMapOf<Long, Pair<Schedule, Schedule>>()
    }
}
