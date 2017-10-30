package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.Date
import maryk.core.protobuf.WireType

/** Definition for Date properties */
class DateDefinition(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Date? = null,
        maxValue: Date? = null,
        fillWithNow: Boolean = false
) : AbstractMomentDefinition<Date>(
        name, index, indexed, searchable, required, final, WireType.VAR_INT, unique, minValue, maxValue, fillWithNow
), IsFixedBytesEncodable<Date> {
    override val byteSize = 8

    override fun createNow() = Date.nowUTC()

    override fun readStorageBytes(context: IsPropertyContext?, length: Int, reader:() -> Byte) = Date.fromByteReader(reader)

    override fun calculateStorageByteLength(value: Date) = this.byteSize

    override fun writeStorageBytes(value: Date, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun readTransportBytes(context: IsPropertyContext?, length: Int, reader: () -> Byte) = Date.ofEpochDay(initLongByVar(reader))

    override fun calculateTransportByteLength(value: Date) = value.epochDay.calculateVarByteLength()

    override fun writeTransportBytes(value: Date, writer: (byte: Byte) -> Unit) {
        val epochDay = value.epochDay
        epochDay.writeVarBytes(writer)
    }

    @Throws(ParseException::class)
    override fun fromString(string: String, context: IsPropertyContext?) = Date.parse(string)
}