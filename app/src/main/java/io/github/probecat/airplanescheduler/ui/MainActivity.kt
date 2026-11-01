package io.github.probecat.airplanescheduler.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.probecat.airplanescheduler.ui.help.HelpActivity
import io.github.probecat.airplanescheduler.ui.schedules.SchedulesScreen
import io.github.probecat.airplanescheduler.ui.settings.SettingsActivity

class MainActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen {
            SchedulesScreen(
                viewModel,
                onSettings = { start<SettingsActivity>() },
                onHelp = { start<HelpActivity>() },
            )
        }
    }
}

inline fun <reified T : AppActivity> Context.start() = startActivity(Intent(this, T::class.java))
