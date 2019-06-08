package maryk.core.properties.types

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.types.TimePrecision.MILLIS
import maryk.core.properties.types.TimePrecision.SECONDS
import maryk.lib.time.DateTime as LibDateTime

typealias DateTime = LibDateTime

fun LibDateTime.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    when (precision) {
        MILLIS -> {
            this.toEpochSecond().writeBytes(writer, 7)
            this.milli.writeBytes(writer)
        }
        SECONDS -> {
            this.toEpochSecond().writeBytes(writer, 7)
        }
    }
}

fun LibDateTime.Companion.byteSize(precision: TimePrecision) = when (precision) {
    MILLIS -> 9
    SECONDS -> 7
}

fun LibDateTime.Companion.fromByteReader(length: Int, reader: () -> Byte) = when (length) {
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
