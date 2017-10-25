package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteSize
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.random
import maryk.core.protobuf.WireType

object SInt64 : NumberDescriptor<Long>(
        size = 8,
        wireType = WireType.VAR_INT
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Long = initLong(reader)
    override fun writeStorageBytes(value: Long, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initLongByVar(reader).decodeZigZag()
    override fun calculateTransportByteSize(value: Long) = value.encodeZigZag().calculateVarByteSize()
    override fun writeTransportBytes(value: Long, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }
    override fun ofString(value: String) = value.toLong()
    override fun createRandom() = Long.random()
}