package io.github.probecat.airplanescheduler.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import io.github.probecat.airplanescheduler.mock.Mock
import rikka.shizuku.Shizuku

enum class ShizukuState { READY, OFFLINE, PERMISSION_NEEDED }

object ShizukuStatus {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 1

    fun current(): ShizukuState = if (Mock.active) Mock.config.value.shizuku else real()

    private fun real(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.OFFLINE
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.PERMISSION_NEEDED
        }
    } catch (_: RuntimeException) {
        ShizukuState.OFFLINE
    }

    // False when the dialog can't show, such as after "Deny and don't ask again". Mock data acts as if
    // it can't, since Shizuku's own dialog can't be mocked.
    fun request(): Boolean = !Mock.active && try {
        when {
            !Shizuku.pingBinder() -> false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> true
            Shizuku.shouldShowRequestPermissionRationale() -> false
            else -> {
                Shizuku.requestPermission(REQUEST)
                true
            }
        }
    } catch (_: RuntimeException) {
        false
    }

    // Where to get Shizuku when it isn't installed.
    fun repository(): Intent = Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku".toUri())

    fun launchIntent(context: Context): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        return if (Mock.active) Mock.shizukuApp(intent) else intent
    }
}
