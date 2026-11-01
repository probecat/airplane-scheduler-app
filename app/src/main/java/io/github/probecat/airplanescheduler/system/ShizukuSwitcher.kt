package io.github.probecat.airplanescheduler.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import io.github.probecat.airplanescheduler.IAirplaneService
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

// Runs [AirplaneService] in a Shizuku process with shell privileges for each switch.
class ShizukuSwitcher(private val context: Context) : Switcher {
    private var timedOutAt = Long.MIN_VALUE / 2

    // A freshly started process receives the binder asynchronously, so this waits up to 7 s. Right after
    // a wait ran out, Shizuku isn't running, so the next steps of the same alarm don't wait again. A tap
    // waits regardless, since Shizuku sends the binder only once the app becomes active.
    override suspend fun connect(tap: Boolean): Boolean {
        val waitedRecently = !tap && SystemClock.elapsedRealtime() - timedOutAt < 30_000
        if (Shizuku.pingBinder() || waitedRecently) return Shizuku.pingBinder()
        val received = withTimeoutOrNull(7000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Shizuku.OnBinderReceivedListener {
                    override fun onBinderReceived() {
                        Shizuku.removeBinderReceivedListener(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                Shizuku.addBinderReceivedListener(listener)
                continuation.invokeOnCancellation { Shizuku.removeBinderReceivedListener(listener) }
            }
        } != null
        if (!received) timedOutAt = SystemClock.elapsedRealtime()
        return received
    }

    // None worked when Shizuku can't be reached.
    override suspend fun set(enabled: Boolean, changes: Changes): Changes {
        if (changes.isEmpty || !connect()) return Changes.NONE
        return try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return Changes.NONE
            val args = Shizuku.UserServiceArgs(ComponentName(context, AirplaneService::class.java))
                .processNameSuffix("airplane")
                .daemon(false)
            val (binder, connection) = withTimeoutOrNull(10_000) { bind(args) } ?: return Changes.NONE
            withContext(Dispatchers.IO) {
                try {
                    Changes.of(
                        IAirplaneService.Stub.asInterface(binder).setEnabled(enabled, changes.airplane, changes.wifi),
                    )
                } catch (_: Exception) {
                    Changes.NONE
                } finally {
                    try {
                        Shizuku.unbindUserService(args, connection, true)
                    } catch (_: RuntimeException) {
                    }
                }
            }
        } catch (_: RuntimeException) {
            Changes.NONE
        }
    }

    // Null when the service disconnects before it connects. One that never connects is let go.
    private suspend fun bind(args: Shizuku.UserServiceArgs): Pair<IBinder, ServiceConnection>? =
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (continuation.isActive) continuation.resume(binder to this)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            continuation.invokeOnCancellation {
                try {
                    Shizuku.unbindUserService(args, connection, true)
                } catch (_: RuntimeException) {
                }
            }
            Shizuku.bindUserService(args, connection)
        }
}
