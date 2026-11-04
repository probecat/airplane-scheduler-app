package io.github.probecat.airplanescheduler.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import rikka.shizuku.Shizuku
import rikka.sui.Sui

object ShizukuStatus {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST = 1

    fun current(): AccessState = try {
        when {
            !Shizuku.pingBinder() -> AccessState.OFFLINE
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> AccessState.READY
            else -> AccessState.PERMISSION_NEEDED
        }
    } catch (_: RuntimeException) {
        AccessState.OFFLINE
    }

    val sui: Boolean get() = Sui.isSui()

    // False when Android cannot show the dialog, for example after "Deny and don't ask again".
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
