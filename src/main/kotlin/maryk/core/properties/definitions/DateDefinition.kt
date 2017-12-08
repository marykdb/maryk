package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.Date
import maryk.core.protobuf.WireType

/** Definition for Date properties */
class DateDefinition(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val unique: Boolean = false,
        override val minValue: Date? = null,
        override val maxValue: Date? = null,
        override val fillWithNow: Boolean = false
) : IsMomentDefinition<Date>, IsSerializableFixedBytesEncodable<Date, IsPropertyContext> {
    override val wireType = WireType.VAR_INT
    override val byteSize = 8

    override fun createNow() = Date.nowUTC()

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Date.fromByteReader(reader)

    override fun calculateStorageByteLength(value: Date) = this.byteSize

    override fun writeStorageBytes(value: Date, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: IsPropertyContext?) = Date.ofEpochDay(initLongByVar(reader))

    override fun calculateTransportByteLength(value: Date) = value.epochDay.calculateVarByteLength()

    override fun writeTransportBytes(value: Date, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: IsPropertyContext?) {
        val epochDay = value.epochDay
        epochDay.writeVarBytes(writer)
    }

    override fun fromString(string: String) = Date.parse(string)
}