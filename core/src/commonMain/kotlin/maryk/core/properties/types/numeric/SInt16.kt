package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.SInt16Type
import maryk.core.protobuf.WireType.VAR_INT
import kotlin.random.Random

object SInt16 : NumberDescriptor<Short>(
    size = 2,
    wireType = VAR_INT,
    type = SInt16Type,
    zero = 0.toShort()
) {
    override fun sum(value1: Short, value2: Short) = (value1 + value2).toShort()
    override fun divide(value1: Short, value2: Short) = (value1 / value2).toShort()
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Short = initShort(reader)
    override fun writeStorageBytes(value: Short, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initShortByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Short) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Short, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toShort()
    override fun ofDouble(double: Double) = double.toInt().toShort()
    override fun toDouble(value: Short) = value.toDouble()
    override fun ofInt(int: Int) = int.toShort()
    override fun ofLong(long: Long) = long.toShort()
    override fun createRandom() = Random.nextInt(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt() + 1).toShort()
    override fun isOfType(value: Any) = value is Short
}
