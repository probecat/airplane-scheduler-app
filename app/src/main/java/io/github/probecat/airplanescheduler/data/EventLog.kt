package io.github.probecat.airplanescheduler.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Recent alarms, switches, access changes and taps for the debug info, oldest first. Each entry is a plain line, so
// any version can show the lines of another.
class EventLog(context: Context) {
    private val file = File(context.noBackupFilesDir, "events.log")
    private val lines = MutableStateFlow(read())

    val entries: StateFlow<List<String>> = lines.asStateFlow()

    fun add(text: String) {
        val next = (lines.value + "${STAMP.format(LocalDateTime.now())}  $text").takeLast(MAX)
        lines.value = next
        try {
            file.writeText(next.joinToString("\n"))
        } catch (_: IOException) {
        }
    }

    private fun read(): List<String> = try {
        if (file.exists()) file.readLines().filter { it.isNotBlank() }.takeLast(MAX) else emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    private companion object {
        const val MAX = 200
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }
}
