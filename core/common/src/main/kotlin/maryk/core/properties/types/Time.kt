package maryk.core.properties.types

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.writeBytes
import maryk.lib.time.Time

enum class TimePrecision(override val index: Int): IndexedEnum<TimePrecision> {
    SECONDS(0), MILLIS(1);

    companion object: IndexedEnumDefinition<TimePrecision>(
        "TimePrecision", TimePrecision::values
    )
}

internal fun Time.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    when (precision) {
        TimePrecision.MILLIS -> (this.toSecondsOfDay() * 1000 + this.milli).writeBytes(writer)
        TimePrecision.SECONDS -> this.toSecondsOfDay().writeBytes(writer, 3)
    }
}

internal fun Time.Companion.byteSize(precision: TimePrecision) = when (precision) {
    TimePrecision.MILLIS -> 4
    TimePrecision.SECONDS -> 3
}

internal fun Time.Companion.fromByteReader(length: Int, reader: () -> Byte): Time = when (length) {
    4 -> Time.ofMilliOfDay(initInt(reader))
    3 -> Time.ofSecondOfDay(initInt(reader, length))
    else -> throw IllegalArgumentException("Invalid length for bytes for Time conversion: $length")
}
