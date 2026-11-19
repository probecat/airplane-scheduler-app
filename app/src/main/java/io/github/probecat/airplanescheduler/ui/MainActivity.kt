package io.github.probecat.airplanescheduler.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.probecat.airplanescheduler.ui.help.HelpActivity
import io.github.probecat.airplanescheduler.ui.schedules.SchedulesScreen
import io.github.probecat.airplanescheduler.ui.settings.SettingsActivity

class MainActivity : AppActivity() {
    // The schedule a tapped notification asks to open.
    private var opening by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity gets its first intent again, but the sheet it opened is already restored.
        if (savedInstanceState == null) open(intent)
        setScreen {
            SchedulesScreen(
                viewModel,
                onSettings = { start<SettingsActivity>() },
                onHelp = { start<HelpActivity>() },
                opening = opening,
                onOpen = { opening = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    private fun open(intent: Intent) {
        if (intent.hasExtra(SCHEDULE)) opening = intent.getLongExtra(SCHEDULE, 0)
    }

    companion object {
        const val SCHEDULE = "schedule"
    }
}

inline fun <reified T : AppActivity> Context.start() = startActivity(Intent(this, T::class.java))
