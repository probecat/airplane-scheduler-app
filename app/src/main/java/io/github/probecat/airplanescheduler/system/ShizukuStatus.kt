package io.github.probecat.airplanescheduler.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import rikka.shizuku.Shizuku
import rikka.sui.Sui

enum class ShizukuState { READY, OFFLINE, PERMISSION_NEEDED }

object ShizukuStatus {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 1

    fun current(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.OFFLINE
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.PERMISSION_NEEDED
        }
    } catch (_: RuntimeException) {
        ShizukuState.OFFLINE
    }

    val sui: Boolean get() = Sui.isSui()

    // False when the dialog can't show, such as after "Deny and don't ask again".
    fun request(): Boolean = try {
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

    fun repository(): Intent = Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku".toUri())

    fun launchIntent(context: Context): Intent? = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
}
