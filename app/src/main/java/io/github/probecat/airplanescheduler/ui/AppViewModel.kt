package io.github.probecat.airplanescheduler.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.probecat.airplanescheduler.core.ScheduleEdits
import io.github.probecat.airplanescheduler.core.ScheduleStatus
import io.github.probecat.airplanescheduler.core.ScheduleTime
import io.github.probecat.airplanescheduler.data.Schedule
import io.github.probecat.airplanescheduler.data.Settings
import io.github.probecat.airplanescheduler.system.Access
import io.github.probecat.airplanescheduler.system.AirplaneController
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.system.Notifier
import io.github.probecat.airplanescheduler.system.ScheduleUpdater
import io.github.probecat.airplanescheduler.system.notificationsAllowed
import java.time.LocalDateTime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App

    val schedules: StateFlow<List<Schedule>> = app.schedules.schedules
        .map(::sorted)
        .stateIn(viewModelScope, SharingStarted.Eagerly, sorted(app.schedules.all))

    val settings: StateFlow<Settings> = app.settings.settings

    val access: StateFlow<Access> = app.access

    // Android's minute tick follows clock and time zone changes. A delay sleeps through them.
    val now: StateFlow<LocalDateTime> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(LocalDateTime.now())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(LocalDateTime.now())
        awaitClose { app.unregisterReceiver(receiver) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    // The schedule of the running window when the app missed its start. The other flows only tell when to look again.
    val missed: StateFlow<Long?> = combine(
        app.schedules.schedules,
        now,
        app.engine.states,
        app.airplane,
        AirplaneController.inFlightCount,
    ) { _, now, _, _, _ -> AirplaneController.missedSchedule(app, now)?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Notices tell whether Shizuku is ready. A new process learns this only after a short time.
    fun refresh() {
        viewModelScope.launch {
            app.switcher.connect(tap = true)
            app.refreshAccess()
            Notifier.refresh(app)
        }
    }

    fun requestAccess(explain: () -> Unit) {
        viewModelScope.launch {
            if (!app.switcher.request()) explain()
            app.refreshAccess()
        }
    }

    fun newSchedule(): Schedule {
        val base = Schedule(
            id = app.schedules.nextId(),
            start = 0,
            end = 0,
            days = emptySet(),
            disableWifi = false,
            enableWifi = false,
        )
        val start = ScheduleTime.freeSlot(ScheduleTime.minuteOf(LocalDateTime.now()), ScheduleTime.HOUR, 30) {
                start,
                end,
            ->
            conflict(base.copy(start = start, end = end)) == null
        }
        return base.copy(start = start, end = (start + ScheduleTime.HOUR) % ScheduleTime.DAY)
    }

    fun isNew(schedule: Schedule): Boolean = app.schedules.get(schedule.id) == null

    fun conflict(schedule: Schedule): Schedule? =
        ScheduleEdits.conflict(app.schedules.all, schedule, LocalDateTime.now())

    // Returns the overlapping schedule instead of switching on.
    fun setEnabled(schedule: Schedule, enabled: Boolean): Schedule? =
        ScheduleUpdater.launch(app, ScheduleEdits.setEnabled(app.schedules.all, schedule, enabled, LocalDateTime.now()))
            .also { if (it == null) app.log.add("Schedule #${schedule.id} ${if (enabled) "enabled" else "disabled"}") }

    // These taps act only while the schedule status is still the one they were [offered] for. If the user already did
    // the same tap on the notification, an older card or dialog does nothing and returns null or false.

    fun skip(id: Long, offered: ScheduleStatus): Boolean {
        val schedule = offered(id, offered) ?: return false
        val missed = (offered as? ScheduleStatus.Running)?.missed == true
        app.log.add("App action SKIP for #$id")
        commit(ScheduleEdits.skip(app.schedules.all, schedule, LocalDateTime.now(), missed))
        return true
    }

    fun postpone(id: Long, offered: ScheduleStatus): Boolean {
        val schedule = offered(id, offered) ?: return false
        app.log.add("App action POSTPONE for #$id")
        commit(ScheduleEdits.postpone(app.schedules.all, schedule, LocalDateTime.now(), settings.value.postponeMinutes))
        return true
    }

    fun unskip(id: Long, offered: ScheduleStatus): ScheduleEdits.Result? {
        val schedule = offered(id, offered) ?: return null
        app.log.add("App action UNDO for #$id")
        return ScheduleEdits.unskip(app.schedules.all, schedule, LocalDateTime.now()).also(::commit)
    }

    fun catchUp(id: Long, offered: ScheduleStatus, done: (Boolean) -> Unit): Boolean {
        offered(id, offered) ?: return false
        app.log.add("App action TURN_ON for #$id")
        app.launch { done(ScheduleUpdater.catchUp(app)) }
        return true
    }

    private fun offered(id: Long, offered: ScheduleStatus): Schedule? =
        app.schedules.get(id)?.takeIf { ScheduleUpdater.status(app, it, LocalDateTime.now()) == offered }

    fun save(edited: Schedule): Schedule? {
        val verb = if (isNew(edited)) "added" else "edited"
        return ScheduleUpdater.launch(app, ScheduleEdits.saveSheet(app.schedules.all, edited, LocalDateTime.now()))
            .also { if (it == null) app.log.add("Schedule #${edited.id} $verb") }
    }

    // True once, when the first schedule is saved and notifications are not allowed yet.
    fun asksNotifications(): Boolean {
        if (app.settings.current.askedNotifications) return false
        updateSettings { it.copy(askedNotifications = true) }
        return !notificationsAllowed(app)
    }

    fun delete(schedule: Schedule) {
        app.log.add("Schedule #${schedule.id} deleted")
        commit(ScheduleEdits.delete(app.schedules.all, schedule.id))
    }

    fun restore(schedule: Schedule) {
        app.log.add("Schedule #${schedule.id} restored")
        commit(ScheduleEdits.restore(app.schedules.all, schedule, app.schedules.nextId(), LocalDateTime.now()))
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val before = app.settings.current
        app.settings.update(transform)
        if (before.backend != app.settings.current.backend) app.refreshAccess()
        if (before.postponeMinutes != app.settings.current.postponeMinutes) Notifier.refresh(app)
    }

    fun resetSettings() = updateSettings { Settings() }

    private fun commit(result: ScheduleEdits.Result) {
        ScheduleUpdater.launch(app, result)
    }

    private fun sorted(schedules: List<Schedule>) = schedules.sortedWith(compareBy({ it.start }, { it.end }, { it.id }))
}
