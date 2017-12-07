package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.Time
import maryk.core.properties.types.TimePrecision
import maryk.core.protobuf.WireType

/**
 * Definition for Time properties
 */
class TimeDefinition(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = true,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Time? = null,
        maxValue: Time? = null,
        fillWithNow: Boolean = false,
        precision: TimePrecision = TimePrecision.SECONDS
) : AbstractTimeDefinition<Time>(
        indexed, searchable, required, final, WireType.VAR_INT, unique, minValue, maxValue, fillWithNow, precision
), IsSerializableFixedBytesEncodable<Time, IsPropertyContext> {
    override val byteSize = Time.byteSize(precision)

    override fun createNow() = Time.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte)
            = Time.fromByteReader(length, reader)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) = when(this.precision) {
        TimePrecision.SECONDS -> Time.ofSecondOfDay(initIntByVar(reader))
        TimePrecision.MILLIS -> Time.ofMilliOfDay(initIntByVar(reader))
    }

    override fun calculateTransportByteLength(value: Time) = when(this.precision) {
        TimePrecision.SECONDS -> value.toSecondsOfDay().calculateVarByteLength()
        TimePrecision.MILLIS -> value.toMillisOfDay().calculateVarByteLength()
    }

    override fun writeTransportBytes(value: Time, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: IsPropertyContext?) {
        val toEncode = when(this.precision) {
            TimePrecision.SECONDS -> value.toSecondsOfDay()
            TimePrecision.MILLIS -> value.toMillisOfDay()
        }
        toEncode.writeVarBytes(writer)
    }

    override fun fromString(string: String) = Time.parse(string)
}