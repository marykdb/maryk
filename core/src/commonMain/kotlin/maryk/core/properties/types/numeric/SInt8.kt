package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initByte
import maryk.core.extensions.bytes.initByteByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.SInt8Type
import maryk.core.protobuf.WireType.VAR_INT
import kotlin.random.Random

object SInt8 : NumberDescriptor<Byte>(
    size = 1,
    wireType = VAR_INT,
    type = SInt8Type,
    zero = 0.toByte()
) {
    override fun sum(value1: Byte, value2: Byte) = (value1 + value2).toByte()
    override fun divide(value1: Byte, value2: Byte) = (value1 / value2).toByte()
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Byte = initByte(reader)
    override fun writeStorageBytes(value: Byte, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initByteByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Byte) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Byte, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toByte()
    override fun ofDouble(double: Double) = double.toByte()
    override fun toDouble(value: Byte) = value.toDouble()
    override fun ofInt(int: Int) = int.toByte()
    override fun ofLong(long: Long) = long.toByte()
    override fun createRandom() = Random.nextInt(127).toByte()
    override fun isOfType(value: Any) = value is Byte
}
