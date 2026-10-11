package io.github.probecat.airplanescheduler.mock

import android.os.Bundle
import io.github.probecat.airplanescheduler.ui.AppActivity
import io.github.probecat.airplanescheduler.ui.open

class MockActivity : AppActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreen { MockScreen(viewModel, onOpen = { open(it) }, onBack = ::finish) }
    }
}
