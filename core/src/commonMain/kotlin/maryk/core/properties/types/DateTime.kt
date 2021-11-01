package maryk.core.properties.types

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.core.properties.types.TimePrecision.SECONDS
import maryk.lib.time.DateTime.ofEpochSecond

fun LocalDateTime.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    val epochInstant = this.toInstant(TimeZone.UTC)
    when (precision) {
        MILLIS -> {
            epochInstant.epochSeconds.writeBytes(writer, 7)
            (epochInstant.nanosecondsOfSecond / 1000000).toShort().writeBytes(writer)
        }
        SECONDS -> {
            epochInstant.epochSeconds.writeBytes(writer, 7)
        }
    }
}

fun LocalDateTime.Companion.byteSize(precision: TimePrecision) = when (precision) {
    MILLIS -> 9
    SECONDS -> 7
}

fun LocalDateTime.Companion.fromByteReader(length: Int, reader: () -> Byte) = when (length) {
    7 -> ofEpochSecond(
        initLong(reader, 7)
    )
    9 -> ofEpochSecond(
        initLong(reader, 7),
        initShort(reader)
    )
    else ->
        throw IllegalArgumentException("Invalid length for bytes for DateTime conversion: $length")
}
