package io.github.probecat.airplanescheduler.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Schedule(
    val id: Long,
    val name: String = "",
    // Minutes since midnight. An end before the start is on the next day; an equal one is invalid.
    val start: Int,
    val end: Int,
    // Days the window starts on. None means it runs once, starting on [date].
    val days: Set<DayOfWeek> = EVERY_DAY,
    @Serializable(with = DateSerializer::class)
    val date: LocalDate? = null,
    val enabled: Boolean = true,
    val disableWifi: Boolean = true,
    val enableWifi: Boolean = true,
) {
    val once: Boolean get() = days.isEmpty()

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}

private object DateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDate = try {
        LocalDate.parse(decoder.decodeString())
    } catch (e: DateTimeParseException) {
        throw SerializationException(e.message)
    }
}
