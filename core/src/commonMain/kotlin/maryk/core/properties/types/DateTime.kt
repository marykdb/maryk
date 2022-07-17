package maryk.core.properties.types

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.core.properties.types.TimePrecision.NANOS
import maryk.core.properties.types.TimePrecision.SECONDS

fun LocalDateTime.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    val epochInstant = this.toInstant(TimeZone.UTC)
    when (precision) {
        NANOS -> {
            epochInstant.epochSeconds.writeBytes(writer, 7)
            epochInstant.nanosecondsOfSecond.toUInt().writeBytes(writer)
        }
        MILLIS -> {
            epochInstant.epochSeconds.writeBytes(writer, 7)
            (epochInstant.nanosecondsOfSecond / 1000000).toShort().writeBytes(writer)
        }
        SECONDS -> {
            epochInstant.epochSeconds.writeBytes(writer, 7)
        }
    }
}

fun DateTimeDefinition.Companion.byteSize(precision: TimePrecision) = when (precision) {
    NANOS -> 11
    MILLIS -> 9
    SECONDS -> 7
}

fun LocalDateTime.Companion.fromByteReader(length: Int, reader: () -> Byte) = when (length) {
    7 -> Instant.fromEpochSeconds(
        initLong(reader, 7)
    )
    9 -> Instant.fromEpochSeconds(
        initLong(reader, 7),
        initShort(reader) * 1000000
    )
    11 -> Instant.fromEpochSeconds(
        initLong(reader, 7),
        initUInt(reader).toInt()
    )
    else ->
        throw IllegalArgumentException("Invalid length for bytes for DateTime conversion: $length")
}.toLocalDateTime(TimeZone.UTC)
