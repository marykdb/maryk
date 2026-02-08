package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.initULongByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.UInt64Type
import kotlin.random.Random
import kotlin.random.nextULong

/** Object for 64 bit/8 byte unsigned integers */
object UInt64 : UnsignedNumberDescriptor<ULong>(
    size = ULong.SIZE_BYTES,
    MIN_VALUE = ULong.MIN_VALUE,
    MAX_VALUE = ULong.MAX_VALUE,
    type = UInt64Type,
    zero = 0.toULong()
) {
    override fun sum(value1: ULong, value2: ULong) = value1 + value2
    override fun divide(value1: ULong, value2: ULong) = value1 / value2
    override fun fromStorageByteReader(length: Int, reader: () -> Byte) = initULong(reader)
    override fun writeStorageBytes(value: ULong, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initULongByVar(reader)
    override fun calculateTransportByteLength(value: ULong) = value.calculateVarByteLength()
    override fun writeTransportBytes(value: ULong, writer: (byte: Byte) -> Unit) {
        value.writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toULong()
    override fun ofDouble(double: Double) = double.toLong().toULong()
    override fun toDouble(value: ULong) = value.toDouble()
    override fun ofInt(int: Int) = int.toULong()
    override fun ofLong(long: Long) = long.toULong()
    override fun createRandom() = Random.nextULong()
    override fun isOfType(value: Any) = value is ULong
}
