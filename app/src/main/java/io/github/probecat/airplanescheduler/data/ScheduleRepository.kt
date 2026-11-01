package io.github.probecat.airplanescheduler.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// All schedules as one JSON list in SharedPreferences: tiny, and synchronous for receivers.
class ScheduleRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(decode(prefs.getString(KEY, null)))

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

    companion object {
        private const val PREFS = "schedules"
        private const val KEY = "schedules"
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun encode(schedules: List<Schedule>): String = json.encodeToString(schedules)

        fun decode(value: String?): List<Schedule> {
            if (value == null) return emptyList()
            return try {
                json.decodeFromString(value)
            } catch (_: SerializationException) {
                emptyList()
            } catch (_: IllegalArgumentException) {
                emptyList()
            }
        }
    }
}
