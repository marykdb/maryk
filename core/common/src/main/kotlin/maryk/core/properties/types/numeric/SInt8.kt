package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initByte
import maryk.core.extensions.bytes.initByteByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.protobuf.WireType
import maryk.lib.extensions.random

object SInt8 : NumberDescriptor<Byte>(
    size = 1,
    wireType = WireType.VAR_INT,
    type = NumberType.SINT8
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Byte = initByte(reader)
    override fun writeStorageBytes(value: Byte, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initByteByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Byte) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Byte, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }
    override fun ofString(value: String) = value.toByte()
    override fun ofDouble(value: Double) = value.toByte()
    override fun ofInt(value: Int) = value.toByte()
    override fun ofLong(value: Long) = value.toByte()
    override fun createRandom() = Byte.random()
    override fun isOfType(value: Any) = value is Byte
}
