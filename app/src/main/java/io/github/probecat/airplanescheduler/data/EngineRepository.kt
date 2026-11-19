package io.github.probecat.airplanescheduler.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.probecat.airplanescheduler.core.Boundary
import io.github.probecat.airplanescheduler.core.ConnectivityPlan.Changes
import io.github.probecat.airplanescheduler.core.Engine
import io.github.probecat.airplanescheduler.core.Reconcile
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
    // On the device only, as a restored install has no running window. See [Prefs].
    private val prefs = Prefs.device(context, LEGACY + KEY)
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
    private data class Stored(
        val started: StoredStart? = null,
        val wifiKeptUntil: String? = null,
        val owed: StoredOwed? = null,
    )

    @Serializable
    private data class StoredStart(val window: String, val changes: Int, val wanted: Int, val already: Int)

    @Serializable
    private data class StoredOwed(
        val airplaneOn: Boolean,
        val wifiOn: Boolean,
        val touched: Int,
        val ended: StoredStart? = null,
        val schedule: Long? = null,
    )

    companion object {
        private const val KEY = "engine"
        private val LEGACY =
            listOf("startedWindow", "startedChanges", "startedWanted", "startedAlready", "wifiKeptUntil")
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(state: Engine.State): String = json.encodeToString(
            Stored(
                state.started?.let(::store),
                state.wifiKeptUntil?.toString(),
                state.owed?.let {
                    StoredOwed(it.seen.airplaneOn, it.seen.wifiOn, it.touched.bits, it.ended?.let(::store), it.schedule)
                },
            ),
        )

        fun decode(value: String): Engine.State = try {
            val stored = json.decodeFromString<Stored>(value)
            Engine.State(
                stored.started?.let(::load),
                stored.wifiKeptUntil?.let(LocalDateTime::parse),
                stored.owed?.let {
                    Reconcile.Owed(
                        Engine.Device(it.airplaneOn, it.wifiOn),
                        Changes.of(it.touched),
                        it.ended?.let(::load),
                        it.schedule,
                    )
                },
            )
        } catch (_: SerializationException) {
            Engine.State()
        } catch (_: IllegalArgumentException) {
            Engine.State()
        } catch (_: DateTimeParseException) {
            Engine.State()
        }

        private fun store(started: Boundary.Started) = StoredStart(
            started.window.toString(),
            started.changes.bits,
            started.wanted.bits,
            started.already.bits,
        )

        private fun load(stored: StoredStart) = Boundary.Started(
            LocalDateTime.parse(stored.window),
            Changes.of(stored.changes),
            Changes.of(stored.wanted),
            Changes.of(stored.already),
        )

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
