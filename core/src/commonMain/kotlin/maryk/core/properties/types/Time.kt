@file:Suppress("UnusedReceiverParameter")

package maryk.core.properties.types

import kotlinx.datetime.LocalTime
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum

enum class TimePrecision(
    override val index: UInt,
    override val alternativeNames: Set<String>? = null
) : IndexedEnumComparable<TimePrecision>, IsCoreEnum {
    SECONDS(1u), MILLIS(2u), NANOS(4u);

    companion object : IndexedEnumDefinition<TimePrecision>(
        TimePrecision::class, { entries }
    )
}

internal fun LocalTime.writeBytes(precision: TimePrecision, writer: (byte: Byte) -> Unit) {
    when (precision) {
        TimePrecision.NANOS -> this.toNanosecondOfDay().toULong().writeBytes(writer, 6)
        TimePrecision.MILLIS -> this.toMillisecondOfDay().toUInt().writeBytes(writer)
        TimePrecision.SECONDS -> this.toSecondOfDay().toUInt().writeBytes(writer, 3)
    }
}

internal fun TimeDefinition.Companion.byteSize(precision: TimePrecision) = when (precision) {
    TimePrecision.NANOS -> 6
    TimePrecision.MILLIS -> 4
    TimePrecision.SECONDS -> 3
}

internal fun LocalTime.Companion.fromByteReader(length: Int, reader: () -> Byte): LocalTime = when (length) {
    6 -> fromNanosecondOfDay(initULong(reader, 6).toLong())
    4 -> fromMillisecondOfDay(initUInt(reader).toInt())
    3 -> fromSecondOfDay(initUInt(reader, length).toInt())
    else -> throw IllegalArgumentException("Invalid length for bytes for Time conversion: $length")
}
