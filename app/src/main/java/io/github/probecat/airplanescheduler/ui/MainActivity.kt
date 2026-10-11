package io.github.probecat.airplanescheduler.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.probecat.airplanescheduler.BuildConfig
import io.github.probecat.airplanescheduler.mock.Destination
import io.github.probecat.airplanescheduler.mock.MockActivity
import io.github.probecat.airplanescheduler.mock.MockLaunch
import io.github.probecat.airplanescheduler.system.App
import io.github.probecat.airplanescheduler.ui.help.DebugActivity
import io.github.probecat.airplanescheduler.ui.help.HelpActivity
import io.github.probecat.airplanescheduler.ui.schedules.SchedulesScreen
import io.github.probecat.airplanescheduler.ui.settings.SettingsActivity

class MainActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleMock(intent)
        setScreen {
            SchedulesScreen(
                viewModel,
                onSettings = { start<SettingsActivity>() },
                onHelp = { start<HelpActivity>() },
                onMock = if (BuildConfig.DEBUG) ({ start<MockActivity>() }) else null,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMock(intent)
    }

    private fun handleMock(intent: Intent) {
        MockLaunch.handle(App.from(this), viewModel, intent)?.let { open(it) }
    }
}

inline fun <reified T : AppActivity> Context.start() = startActivity(Intent(this, T::class.java))

fun Context.open(destination: Destination) {
    val main = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val screens = when (destination) {
        Destination.SCHEDULES -> emptyList()
        Destination.SETTINGS -> listOf(SettingsActivity::class.java)
        Destination.HELP -> listOf(HelpActivity::class.java)
        Destination.DEBUG -> listOf(SettingsActivity::class.java, DebugActivity::class.java)
        Destination.MOCK -> if (BuildConfig.DEBUG) listOf(MockActivity::class.java) else emptyList()
    }
    startActivities((listOf(main) + screens.map { Intent(this, it) }).toTypedArray())
}
