package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import kotlin.random.Random
import kotlin.random.nextUInt

/** Base object for 32 bit/4 byte unsigned integers */
@Suppress("EXPERIMENTAL_API_USAGE")
object UInt32: UnsignedNumberDescriptor<UInt>(
    size = UInt.SIZE_BYTES,
    MIN_VALUE = UInt.MIN_VALUE,
    MAX_VALUE = UInt.MAX_VALUE,
    type = NumberType.UInt32
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte) = (initInt(reader) + Int.MIN_VALUE).toUInt()
    override fun writeStorageBytes(value: UInt, writer: (byte: Byte) -> Unit) = (value.toInt() - Int.MIN_VALUE).writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initIntByVar(reader).toUInt()
    override fun calculateTransportByteLength(value: UInt) = value.toInt().calculateVarByteLength()
    override fun writeTransportBytes(value: UInt, writer: (byte: Byte) -> Unit) {
        value.toInt().writeVarBytes(writer)
    }
    override fun ofString(value: String) = value.toUInt()
    override fun ofDouble(value: Double) = value.toLong().toUInt()
    override fun toDouble(value: UInt) = value.toLong().toDouble()
    override fun ofInt(value: Int) = value.toUInt()
    override fun ofLong(value: Long) = value.toUInt()
    override fun createRandom() = Random.nextUInt()
    override fun isOfType(value: Any) = value == UInt
}
