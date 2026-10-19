package io.github.probecat.airplanescheduler.ui

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.probecat.airplanescheduler.mock.MockDisplay
import io.github.probecat.airplanescheduler.mock.mockCompositionContext
import io.github.probecat.airplanescheduler.ui.theme.AppTheme
import io.github.probecat.airplanescheduler.ui.theme.isDark

abstract class AppActivity : ComponentActivity() {
    protected val viewModel: AppViewModel by viewModels()

    protected fun setScreen(content: @Composable () -> Unit) {
        setContent(mockCompositionContext()) {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            SystemBars(isDark(settings.theme))
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
            AppTheme(settings.theme, settings.dynamicColor) {
                MockDisplay(content)
            }
        }
    }

    // The in-app theme can differ from the system's, so the bar icons follow the app.
    @Composable
    private fun SystemBars(dark: Boolean) {
        DisposableEffect(dark) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }
}
