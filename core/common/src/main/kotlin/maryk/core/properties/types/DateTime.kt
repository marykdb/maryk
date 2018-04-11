package maryk.core.properties.types

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes

typealias DateTime = maryk.lib.time.DateTime

fun maryk.lib.time.DateTime.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    maryk.lib.time.DateTime.Companion
    when (precision) {
        TimePrecision.MILLIS -> {
            this.toEpochSecond().writeBytes(writer, 7)
            this.milli.writeBytes(writer)
        }
        TimePrecision.SECONDS -> {
            this.toEpochSecond().writeBytes(writer, 7)
        }
    }
}

fun maryk.lib.time.DateTime.Companion.byteSize(precision: TimePrecision) = when (precision) {
    TimePrecision.MILLIS -> 9
    TimePrecision.SECONDS -> 7
}

fun maryk.lib.time.DateTime.Companion.fromByteReader(length: Int, reader: () -> Byte) = when (length) {
    7 -> DateTime.ofEpochSecond(
        initLong(reader, 7)
    )
    9 -> DateTime.ofEpochSecond(
        initLong(reader, 7),
        initShort(reader)
    )
    else -> throw IllegalArgumentException("Invalid length for bytes for DateTime conversion: $length")
}
