package maryk.core.properties.types

import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.lib.time.Time

enum class TimePrecision(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<TimePrecision>, IsCoreEnum {
    SECONDS(1u), MILLIS(2u);

    companion object : IndexedEnumDefinition<TimePrecision>(
        "TimePrecision", TimePrecision::values
    )
}

internal fun Time.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    when (precision) {
        TimePrecision.MILLIS -> (this.toSecondsOfDay() * 1000 + this.milli).toUInt().writeBytes(writer)
        TimePrecision.SECONDS -> this.toSecondsOfDay().toUInt().writeBytes(writer, 3)
    }
}

internal fun Time.Companion.byteSize(precision: TimePrecision) = when (precision) {
    TimePrecision.MILLIS -> 4
    TimePrecision.SECONDS -> 3
}

internal fun Time.Companion.fromByteReader(length: Int, reader: () -> Byte): Time = when (length) {
    4 -> ofMilliOfDay(initUInt(reader).toInt())
    3 -> ofSecondOfDay(initUInt(reader, length).toInt())
    else -> throw IllegalArgumentException("Invalid length for bytes for Time conversion: $length")
}
