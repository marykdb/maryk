package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initByte
import maryk.core.extensions.bytes.initByteByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.UInt8Type
import kotlin.random.Random
import kotlin.random.nextUInt

/** Base class for 8 bit/1 byte unsigned integers */
object UInt8 : UnsignedNumberDescriptor<UByte>(
    size = UByte.SIZE_BYTES,
    MIN_VALUE = UByte.MIN_VALUE,
    MAX_VALUE = UByte.MAX_VALUE,
    type = UInt8Type,
    zero = 0.toUByte()
) {
    override fun sum(value1: UByte, value2: UByte) = (value1 + value2).toUByte()
    override fun divide(value1: UByte, value2: UByte) = (value1 / value2).toUByte()
    override fun fromStorageByteReader(length: Int, reader: () -> Byte) = (initByte(reader) + Byte.MIN_VALUE).toUByte()
    override fun writeStorageBytes(value: UByte, writer: (byte: Byte) -> Unit) =
        (value.toByte() - Byte.MIN_VALUE).toByte().writeBytes(writer)

    override fun readTransportBytes(reader: () -> Byte) = initByteByVar(reader).toUByte()
    override fun calculateTransportByteLength(value: UByte) = value.toInt().calculateVarByteLength()
    override fun writeTransportBytes(value: UByte, writer: (byte: Byte) -> Unit) {
        value.toInt().writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toUByte()
    override fun ofDouble(double: Double) = double.toInt().toUByte()
    override fun toDouble(value: UByte) = value.toLong().toDouble()
    override fun ofInt(int: Int) = int.toUByte()
    override fun ofLong(long: Long) = long.toUByte()
    override fun createRandom() = Random.nextUInt(UByte.MAX_VALUE.toUInt()).toUByte()
    override fun isOfType(value: Any) = value is UByte
}
