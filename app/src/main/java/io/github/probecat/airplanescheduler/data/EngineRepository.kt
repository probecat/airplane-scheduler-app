package io.github.probecat.airplanescheduler.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// The engine state is one JSON value, so a save never writes half of a change.
class EngineRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(read(prefs))

    val states: StateFlow<Engine.State> = flow.asStateFlow()

    var state: Engine.State
        get() = flow.value
        set(value) {
            if (value == flow.value) return
            flow.value = value
            prefs.edit { putString(KEY, encode(value)) }
        }

    @Serializable
    private data class Stored(val started: StoredStart? = null, val wifiKeptUntil: String? = null)

    @Serializable
    private data class StoredStart(val window: String, val changes: Int, val wanted: Int, val already: Int)

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "engine"
        private val LEGACY =
            listOf("startedWindow", "startedChanges", "startedWanted", "startedAlready", "wifiKeptUntil")
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(state: Engine.State): String = json.encodeToString(
            Stored(
                state.started?.let {
                    StoredStart(it.window.toString(), it.changes.bits, it.wanted.bits, it.already.bits)
                },
                state.wifiKeptUntil?.toString(),
            ),
        )

        fun decode(value: String): Engine.State = try {
            val stored = json.decodeFromString<Stored>(value)
            Engine.State(
                stored.started?.let {
                    Boundary.Started(
                        LocalDateTime.parse(it.window),
                        Changes.of(it.changes),
                        Changes.of(it.wanted),
                        Changes.of(it.already),
                    )
                },
                stored.wifiKeptUntil?.let(LocalDateTime::parse),
            )
        } catch (_: SerializationException) {
            Engine.State()
        } catch (_: IllegalArgumentException) {
            Engine.State()
        } catch (_: DateTimeParseException) {
            Engine.State()
        }

        fun legacy(get: (String) -> Any?): Engine.State {
            val changes = get("startedChanges") as? Int ?: 0
            return Engine.State(
                (get("startedWindow") as? String)?.let { window ->
                    Boundary.Started(
                        LocalDateTime.parse(window),
                        Changes.of(changes),
                        wanted = Changes.of(get("startedWanted") as? Int ?: changes),
                        already = Changes.of(get("startedAlready") as? Int ?: 0),
                    )
                },
                (get("wifiKeptUntil") as? String)?.let(LocalDateTime::parse),
            )
        }

        private fun read(prefs: SharedPreferences): Engine.State {
            prefs.getString(KEY, null)?.let { return decode(it) }
            if (LEGACY.none(prefs::contains)) return Engine.State()
            val all = prefs.all
            val state = try {
                legacy(all::get)
            } catch (_: DateTimeParseException) {
                Engine.State()
            }
            prefs.edit {
                putString(KEY, encode(state))
                LEGACY.forEach(::remove)
            }
            return state
        }
    }
}
