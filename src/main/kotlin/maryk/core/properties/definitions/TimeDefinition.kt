package maryk.core.properties.definitions

import maryk.core.extensions.bytes.computeVarByteSize
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Time
import maryk.core.properties.types.TimePrecision
import maryk.core.protobuf.WireType

/**
 * Definition for Time properties
 */
class TimeDefinition(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Time? = null,
        maxValue: Time? = null,
        fillWithNow: Boolean = false,
        precision: TimePrecision = TimePrecision.SECONDS
) : AbstractTimeDefinition<Time>(
        name, index, indexed, searchable, required, final, WireType.VAR_INT, unique, minValue, maxValue, fillWithNow, precision
), IsFixedBytesEncodable<Time> {
    override val byteSize = Time.byteSize(precision)

    override fun createNow() = Time.nowUTC()

    override fun readStorageBytes(length: Int, reader:() -> Byte) = Time.fromByteReader(length, reader)

    override fun readTransportBytes(length: Int, reader: () -> Byte) = when(this.precision) {
        TimePrecision.SECONDS -> Time.ofSecondOfDay(initIntByVar(reader))
        TimePrecision.MILLIS -> Time.ofMilliOfDay(initIntByVar(reader))
    }

    override fun reserveTransportBytes(value: Time) = when(this.precision) {
        TimePrecision.SECONDS -> value.toSecondsOfDay().computeVarByteSize()
        TimePrecision.MILLIS -> value.toMillisOfDay().computeVarByteSize()
    }

    override fun writeTransportBytes(value: Time, writer: (byte: Byte) -> Unit) {
        val toEncode = when(this.precision) {
            TimePrecision.SECONDS -> value.toSecondsOfDay()
            TimePrecision.MILLIS -> value.toMillisOfDay()
        }
        toEncode.writeVarBytes(writer)
    }

    @Throws(ParseException::class)
    override fun fromString(string: String) = Time.parse(string)
}