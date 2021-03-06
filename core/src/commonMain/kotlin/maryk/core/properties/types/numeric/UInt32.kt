package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initUInt
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.UInt32Type
import kotlin.random.Random
import kotlin.random.nextUInt

/** Base object for 32 bit/4 byte unsigned integers */
object UInt32 : UnsignedNumberDescriptor<UInt>(
    size = UInt.SIZE_BYTES,
    MIN_VALUE = UInt.MIN_VALUE,
    MAX_VALUE = UInt.MAX_VALUE,
    type = UInt32Type,
    zero = 0.toUInt()
) {
    override fun sum(value1: UInt, value2: UInt) = value1 + value2
    override fun divide(value1: UInt, value2: UInt) = value1 / value2
    override fun fromStorageByteReader(length: Int, reader: () -> Byte) = initUInt(reader)
    override fun writeStorageBytes(value: UInt, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initUIntByVar(reader)
    override fun calculateTransportByteLength(value: UInt) = value.calculateVarByteLength()
    override fun writeTransportBytes(value: UInt, writer: (byte: Byte) -> Unit) {
        value.writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toUInt()
    override fun ofDouble(double: Double) = double.toLong().toUInt()
    override fun toDouble(value: UInt) = value.toLong().toDouble()
    override fun ofInt(int: Int) = int.toUInt()
    override fun ofLong(long: Long) = long.toUInt()
    override fun createRandom() = Random.nextUInt()
    override fun isOfType(value: Any) = value is UInt
}
