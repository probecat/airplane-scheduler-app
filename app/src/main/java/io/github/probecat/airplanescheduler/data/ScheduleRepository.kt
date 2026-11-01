package io.github.probecat.airplanescheduler.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

// All schedules as one JSON list in SharedPreferences: tiny, and synchronous for receivers.
class ScheduleRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val schedules: StateFlow<List<Schedule>> = state.asStateFlow()

    val all: List<Schedule> get() = state.value

    fun get(id: Long): Schedule? = all.find { it.id == id }

    // Not lowered by deletes, so ids aren't reused while the app runs.
    private var highestId = all.maxOfOrNull { it.id } ?: 0

    fun nextId(): Long = highestId + 1

    fun replaceAll(schedules: List<Schedule>) {
        highestId = maxOf(highestId, schedules.maxOfOrNull { it.id } ?: 0)
        state.value = schedules
        prefs.edit { putString(KEY, encode(schedules)) }
    }

    // Keeps a copy of the text that it cannot read. The next save then does not erase that text, and a later
    // version can still recover it.
    private fun load(): List<Schedule> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val read = read(raw)
        if (read.lost && prefs.all.none { (key, value) -> key.startsWith(UNREADABLE) && value == raw }) {
            prefs.edit { putString(UNREADABLE + System.currentTimeMillis(), raw) }
        }
        return read.schedules
    }

    // [lost] is true when a schedule, or the whole list, cannot be read.
    data class Read(val schedules: List<Schedule>, val lost: Boolean)

    companion object {
        private const val PREFS = "schedules"
        private const val KEY = "schedules"
        private const val UNREADABLE = "unreadable_"
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun encode(schedules: List<Schedule>): String = json.encodeToString(schedules)

        // Reads each schedule separately, so one unreadable schedule does not lose the others.
        fun read(value: String): Read {
            val elements = try {
                json.parseToJsonElement(value) as? JsonArray
            } catch (_: SerializationException) {
                null
            } ?: return Read(emptyList(), lost = true)
            val schedules = elements.mapNotNull { element ->
                try {
                    json.decodeFromJsonElement(Schedule.serializer(), element)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            return Read(schedules, lost = schedules.size < elements.size)
        }
    }
}
