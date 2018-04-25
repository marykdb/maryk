package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.protobuf.WireType
import maryk.lib.extensions.random

object SInt16 : NumberDescriptor<Short>(
    size = 2,
    wireType = WireType.VAR_INT,
    type = NumberType.SInt16
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Short = initShort(reader)
    override fun writeStorageBytes(value: Short, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initShortByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Short) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Short, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }
    override fun ofString(value: String) = value.toShort()
    override fun ofDouble(value: Double) = value.toShort()
    override fun ofInt(value: Int) = value.toShort()
    override fun ofLong(value: Long) = value.toShort()
    override fun createRandom() = Short.random()
    override fun isOfType(value: Any) = value is Short
}
