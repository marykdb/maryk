package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteSize
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.DateTime
import maryk.core.properties.types.TimePrecision
import maryk.core.protobuf.WireType

/**
 * Definition for DateTime properties
 */
class DateTimeDefinition(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: DateTime? = null,
        maxValue: DateTime? = null,
        fillWithNow: Boolean = false,
        precision: TimePrecision = TimePrecision.SECONDS
) : AbstractTimeDefinition<DateTime>(
        name, index, indexed, searchable, required, final, WireType.VAR_INT, unique, minValue, maxValue, fillWithNow, precision
), IsFixedBytesEncodable<DateTime> {
    override val byteSize = DateTime.byteSize(precision)

    override fun createNow() = DateTime.nowUTC()

    override fun readStorageBytes(length: Int, reader:() -> Byte) = DateTime.fromByteReader(length, reader)

    override fun readTransportBytes(length: Int, reader: () -> Byte) = when(this.precision) {
        TimePrecision.SECONDS -> DateTime.ofEpochSecond(initLongByVar(reader))
        TimePrecision.MILLIS -> DateTime.ofEpochMilli(initLongByVar(reader))
    }

    override fun calculateTransportBytes(value: DateTime) = when(this.precision) {
        TimePrecision.SECONDS -> value.toEpochSecond().calculateVarByteSize()
        TimePrecision.MILLIS -> value.toEpochMilli().calculateVarByteSize()
    }

    override fun writeTransportBytes(value: DateTime, writer: (byte: Byte) -> Unit) {
        val epochUnit = when(this.precision) {
            TimePrecision.SECONDS -> value.toEpochSecond()
            TimePrecision.MILLIS -> value.toEpochMilli()
        }
        epochUnit.writeVarBytes(writer)
    }

    @Throws(ParseException::class)
    override fun fromString(string: String) = DateTime.parse(string)
}