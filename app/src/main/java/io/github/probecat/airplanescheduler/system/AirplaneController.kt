package io.github.probecat.airplanescheduler.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import io.github.probecat.airplanescheduler.IAirplaneService
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Ending
import io.github.probecat.airplanescheduler.core.ScheduleResolver
import io.github.probecat.airplanescheduler.mock.Mock
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

object AirplaneController {
    fun onAlarm(context: Context, done: () -> Unit) {
        val app = App.from(context)
        val now = LocalDateTime.now()
        val step = Boundary.atAlarm(app.schedules.all, now, app.settings.started)
        ScheduleUpdater.expire(app, now)
        run(app, step, natural = true, done)
    }

    // A window whose end passed while the phone was off, or that a clock change skipped, is forgotten.
    fun forgetMissedEnd(context: Context) {
        val app = App.from(context)
        val started = app.settings.started ?: return
        if (ScheduleResolver.window(app.schedules.all, LocalDateTime.now())?.start != started.window) {
            app.settings.started = null
            missedEnd(app, started)
        }
    }

    // [natural] is an end at its time rather than one that an edit or action caused, and is reported
    // even when it went as planned.
    fun run(context: Context, step: Boundary.Step, natural: Boolean = false, done: () -> Unit) {
        hold { release ->
            step(App.from(context), step, natural) {
                release()
                done()
            }
        }
    }

    private fun step(app: App, step: Boundary.Step, natural: Boolean, done: () -> Unit) {
        when (step) {
            is Boundary.Step.Start -> {
                val window = step.window
                val airplane = airplaneOn(app)
                val wifi = wifiOn(app)
                val wanted = ConnectivityPlan.start(airplane, wifi, window.disableWifi)
                // A start caught up by hand keeps what the missed one changed and found, so the end still
                // undoes and reports it.
                val started = app.settings.started?.takeIf { it.window == window.start }
                    ?.let { it.copy(wanted = it.wanted + wanted) }
                    ?: Boundary.Started(
                        window.start,
                        Changes.NONE,
                        wanted,
                        ConnectivityPlan.already(airplane, wifi, window.disableWifi),
                    )
                // Saved before Shizuku answers, so an alarm in the meantime doesn't start the window twice.
                app.settings.started = started
                // An end while it was switching, as from a notification's End, undoes what it made.
                set(app, true, wanted) { made ->
                    val current = app.settings.started
                    if (current != null) {
                        app.settings.started = current.copy(changes = current.changes + made)
                        done()
                    } else {
                        set(app, false, made) { done() }
                    }
                }
            }
            is Boundary.Step.End -> {
                val started = app.settings.started
                app.settings.started = null
                val airplane = airplaneOn(app)
                val wifi = wifiOn(app)
                set(app, false, step.changes) { made ->
                    val schedule = step.schedule
                    when {
                        started == null -> {}
                        schedule == null -> missedEnd(app, started)
                        else -> {
                            val ending = Ending.of(started, step.changes, made, airplane, wifi, schedule.enableWifi)
                            if (natural || !ending.failed.isEmpty) Notifier.ended(app, schedule, ending)
                        }
                    }
                    done()
                }
            }
            is Boundary.Step.Move -> {
                app.settings.started = step.started
                done()
            }
            Boundary.Step.None -> done()
        }
    }

    // Tries again what an end couldn't undo. Mock data reads airplane mode as off.
    fun undo(context: Context, changes: Changes, report: Notifier.Report, done: () -> Unit) {
        val app = App.from(context)
        val finish = { made: Changes ->
            val staysOn = airplaneOn(app) && !Mock.active && !made.airplane
            Notifier.ended(app, report, Ending(changes - made, airplaneStaysOn = staysOn, wifiBackOn = made.wifi))
            done()
        }
        if (Mock.active) {
            finish(if (ShizukuStatus.current() == ShizukuState.READY) changes else Changes.NONE)
        } else {
            set(app, false, changes, finish)
        }
    }

    // What the forgotten window left switched is reported, not undone.
    private fun missedEnd(app: App, started: Boundary.Started) {
        val schedule = ScheduleResolver.activeSchedule(app.schedules.all, started.window)
        val undo = ConnectivityPlan.end(started.changes, schedule?.enableWifi ?: true)
        val left = Changes(undo.airplane && airplaneOn(app), undo.wifi && !wifiOn(app))
        if (!left.isEmpty) Notifier.missedEnd(app, schedule, left)
    }

    // Any app can read both states; only changing them needs Shizuku.
    fun airplaneOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun wifiOn(context: Context): Boolean = context.getSystemService(WifiManager::class.java).isWifiEnabled

    private val inFlight = MutableStateFlow(0)

    val inFlightCount: StateFlow<Int> = inFlight.asStateFlow()

    val switching: Boolean get() = inFlight.value > 0

    // Counts as switching until [block] releases, so states on the way to a switch don't read as a missed start.
    fun hold(block: (release: () -> Unit) -> Unit) {
        inFlight.update { it + 1 }
        val released = AtomicBoolean()
        block { if (released.compareAndSet(false, true)) inFlight.update { it - 1 } }
    }

    // Reports the changes that worked; none when Shizuku can't be reached.
    private fun set(app: Context, enabled: Boolean, changes: Changes, done: (Changes) -> Unit) {
        if (changes.isEmpty) {
            done(Changes.NONE)
            return
        }
        inFlight.update { it + 1 }
        val main = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean()
        val finish = { made: Changes ->
            if (completed.compareAndSet(false, true)) {
                inFlight.update { it - 1 }
                done(made)
            }
        }
        val bind = {
            try {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    finish(Changes.NONE)
                } else {
                    val args = Shizuku.UserServiceArgs(ComponentName(app, AirplaneService::class.java))
                        .processNameSuffix("airplane")
                        .daemon(false)
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                            Thread {
                                val made = try {
                                    val service = IAirplaneService.Stub.asInterface(binder)
                                    Changes.of(service.setEnabled(enabled, changes.airplane, changes.wifi))
                                } catch (_: Exception) {
                                    Changes.NONE
                                }
                                main.post { finish(made) }
                                try {
                                    Shizuku.unbindUserService(args, this, true)
                                } catch (_: RuntimeException) {
                                }
                            }.start()
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            finish(Changes.NONE)
                        }
                    }
                    Shizuku.bindUserService(args, connection)
                }
            } catch (_: RuntimeException) {
                finish(Changes.NONE)
            }
        }
        awaitBinder { received -> if (received) bind() else finish(Changes.NONE) }
    }

    // A freshly started process receives the Shizuku binder asynchronously, so wait up to 7 s. Right
    // after a wait ran out, Shizuku isn't running, so the next steps of the same alarm don't wait again.
    // A tap waits regardless, since Shizuku sends the binder only once the app becomes active. Mock data
    // doesn't need it.
    fun awaitBinder(tap: Boolean = false, done: (Boolean) -> Unit) {
        val waitedRecently = !tap && SystemClock.elapsedRealtime() - timedOutAt < 30_000
        if (Mock.active || Shizuku.pingBinder() || waitedRecently) {
            done(Shizuku.pingBinder())
            return
        }
        val main = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean()
        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                Shizuku.removeBinderReceivedListener(this)
                if (completed.compareAndSet(false, true)) {
                    done(true)
                }
            }
        }
        Shizuku.addBinderReceivedListener(listener)
        main.postDelayed({
            Shizuku.removeBinderReceivedListener(listener)
            if (completed.compareAndSet(false, true)) {
                timedOutAt = SystemClock.elapsedRealtime()
                done(false)
            }
        }, 7000)
    }

    private var timedOutAt = Long.MIN_VALUE / 2
}
