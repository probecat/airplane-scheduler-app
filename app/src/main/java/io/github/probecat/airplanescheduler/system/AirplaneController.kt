package io.github.probecat.airplanescheduler.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import io.github.probecat.airplanescheduler.IAirplaneService
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku

object AirplaneController {
    fun onAlarm(context: Context, done: () -> Unit) {
        val app = App.from(context)
        val now = LocalDateTime.now()
        val step = Boundary.atAlarm(app.schedules.all, now, app.settings.started)
        ScheduleUpdater.expire(app, now)
        run(app, step, done)
    }

    fun run(context: Context, step: Boundary.Step, done: () -> Unit) {
        val app = App.from(context)
        when (step) {
            is Boundary.Step.Start -> {
                val window = step.window
                val airplane = airplaneOn(app)
                val wifi = wifiOn(app)
                val wanted = ConnectivityPlan.start(airplane, wifi, window.disableWifi)
                // A start caught up by hand keeps what the missed one changed and found, so the end still
                // undoes it.
                val started = app.settings.started?.takeIf { it.window == window.start }
                    ?: Boundary.Started(
                        window.start,
                        Changes.NONE,
                        ConnectivityPlan.already(airplane, wifi, window.disableWifi),
                    )
                // Saved before Shizuku answers, so an alarm in the meantime doesn't start the window twice.
                app.settings.started = started
                // An end while it was switching, as from Skip, undoes what it made.
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
                app.settings.started = null
                set(app, false, step.changes) { done() }
            }
            is Boundary.Step.Move -> {
                app.settings.started = step.started
                done()
            }
            Boundary.Step.None -> done()
        }
    }

    // Any app can read both states; only changing them needs Shizuku.
    fun airplaneOn(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    private fun wifiOn(context: Context): Boolean = context.getSystemService(WifiManager::class.java).isWifiEnabled

    // Reports the changes that worked; none when Shizuku can't be reached.
    private fun set(app: Context, enabled: Boolean, changes: Changes, done: (Changes) -> Unit) {
        if (changes.isEmpty) {
            done(Changes.NONE)
            return
        }
        val main = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean()
        val finish = { made: Changes ->
            if (completed.compareAndSet(false, true)) {
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

    // A freshly started process receives the Shizuku binder asynchronously, so wait up to 7 s.
    fun awaitBinder(done: (Boolean) -> Unit) {
        if (Shizuku.pingBinder()) {
            done(true)
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
                done(false)
            }
        }, 7000)
    }
}
