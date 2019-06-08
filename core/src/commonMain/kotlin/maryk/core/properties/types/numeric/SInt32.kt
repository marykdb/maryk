package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.SInt32Type
import maryk.core.protobuf.WireType.VAR_INT
import kotlin.random.Random

object SInt32 : NumberDescriptor<Int>(
    size = 4,
    wireType = VAR_INT,
    type = SInt32Type,
    zero = 0
) {
    override fun sum(value1: Int, value2: Int) = value1 + value2
    override fun divide(value1: Int, value2: Int) = value1 / value2
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Int = initInt(reader)
    override fun writeStorageBytes(value: Int, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initIntByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Int) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Int, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toInt()
    override fun ofDouble(double: Double) = double.toInt()
    override fun toDouble(value: Int) = value.toDouble()
    override fun ofInt(int: Int) = int
    override fun ofLong(long: Long) = long.toInt()
    override fun createRandom() = Random.nextInt()
    override fun isOfType(value: Any) = value is Int
}
