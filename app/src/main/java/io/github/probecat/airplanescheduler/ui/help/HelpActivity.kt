package io.github.probecat.airplanescheduler.ui.help

import android.os.Bundle
import io.github.probecat.airplanescheduler.ui.AppActivity

class HelpActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen { HelpScreen(viewModel, onBack = ::finish) }
    }
}
