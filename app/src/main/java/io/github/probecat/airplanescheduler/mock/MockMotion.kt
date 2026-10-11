package io.github.probecat.airplanescheduler.mock

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.probecat.airplanescheduler.BuildConfig

// Compose scales its animations by the system's animator duration scale, which apps can't change.
// This follows it too, unless mock data sets a scale of its own.
private class MockMotion(private val context: Context) : MotionDurationScale {
    private var system = read()

    override val scaleFactor: Float
        get() = (if (Mock.active) Mock.config.value.animationScale else null) ?: system

    // The system scale can only change while the app is in the background.
    fun refresh() {
        system = read()
    }

    private fun read() = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
}

// A recomposer like the one Compose creates for the window, but with the mock animation scale. Sheets
// and dialogs compose under it too. Release builds use Compose's own.
fun ComponentActivity.mockCompositionContext(): CompositionContext? {
    if (!BuildConfig.DEBUG) return null
    val motion = MockMotion(applicationContext)
    lifecycle.addObserver(
        LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) motion.refresh() },
    )
    return window.decorView.createLifecycleAwareWindowRecomposer(motion, lifecycle)
}
