package io.github.probecat.airplanescheduler.ui.settings

import android.os.Bundle
import io.github.probecat.airplanescheduler.ui.AppActivity
import io.github.probecat.airplanescheduler.ui.help.DebugActivity
import io.github.probecat.airplanescheduler.ui.start

class SettingsActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen { SettingsScreen(viewModel, onDebug = { start<DebugActivity>() }, onBack = ::finish) }
    }
}
