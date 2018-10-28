package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import kotlin.random.Random
import kotlin.random.nextULong

/** Object for 64 bit/8 byte unsigned integers */
@Suppress("EXPERIMENTAL_API_USAGE")
object UInt64 : UnsignedNumberDescriptor<ULong>(
    size = ULong.SIZE_BYTES,
    MIN_VALUE = ULong.MIN_VALUE,
    MAX_VALUE = ULong.MAX_VALUE,
    type = NumberType.UInt64
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte) = (initLong(reader) + Long.MIN_VALUE).toULong()
    override fun writeStorageBytes(value: ULong, writer: (byte: Byte) -> Unit) = (value.toLong() - Long.MIN_VALUE).writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initLongByVar(reader).toULong()
    override fun calculateTransportByteLength(value: ULong) = value.toLong().calculateVarByteLength()
    override fun writeTransportBytes(value: ULong, writer: (byte: Byte) -> Unit) {
        value.toLong().writeVarBytes(writer)
    }
    override fun ofString(value: String) = value.toULong()
    override fun ofDouble(value: Double) = value.toLong().toULong()
    override fun toDouble(value: ULong) = value.toLong().toDouble()
    override fun ofInt(value: Int) = value.toULong()
    override fun ofLong(value: Long) = value.toULong()
    override fun createRandom() = Random.nextULong()
    override fun isOfType(value: Any) = value == UInt64
}
