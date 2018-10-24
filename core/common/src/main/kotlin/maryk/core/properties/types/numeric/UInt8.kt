package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initByte
import maryk.core.extensions.bytes.initByteByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.lib.exceptions.ParseException

/** Base class for 8 bit/1 byte unsigned integers */
class UInt8 internal constructor(number: Byte): UnsignedInt<Byte>(number) {
    override fun compareTo(other: UnsignedInt<Byte>) = number.compareTo(other.number)
    override fun toString() = (number.toShort() - Byte.MIN_VALUE).toString()
    override fun toInt() = this.number - Byte.MIN_VALUE
    override fun toLong() = this.number.toLong() - Byte.MIN_VALUE

    companion object : UnsignedNumberDescriptor<UInt8>(
        size = 1,
        MIN_VALUE = UInt8(Byte.MIN_VALUE),
        MAX_VALUE = UInt8(Byte.MAX_VALUE),
        type = NumberType.UInt8
    ) {
        override fun fromStorageByteReader(length: Int, reader: () -> Byte): UInt8 = UInt8(initByte(reader))
        override fun writeStorageBytes(value: UInt8, writer: (byte: Byte) -> Unit) = value.number.writeBytes(writer)
        override fun readTransportBytes(reader: () -> Byte) = UInt8((initByteByVar(reader) + Byte.MIN_VALUE).toByte())
        override fun calculateTransportByteLength(value: UInt8) = (value.number - Byte.MIN_VALUE).calculateVarByteLength()
        override fun writeTransportBytes(value: UInt8, writer: (byte: Byte) -> Unit) {
            val number = value.number.toLong() - Byte.MIN_VALUE
            number.writeVarBytes(writer)
        }
        override fun ofString(value: String) = UInt8((value.toShort() + Byte.MIN_VALUE).toByte())
        override fun ofDouble(value: Double) = value.toInt().toUInt8()
        override fun toDouble(value: UInt8) = value.toLong().toDouble()
        override fun ofInt(value: Int) = value.toUInt8()
        override fun ofLong(value: Long) = value.toInt().toUInt8()
        override fun createRandom() = UInt8(SInt8.createRandom())
        override fun isOfType(value: Any) = value == UInt8
    }
}

fun Byte.toUInt8() = if (this >= 0) {
    UInt8((this + Byte.MIN_VALUE).toByte())
} else { throw ParseException("Negative Byte not allowed $this") }

fun Int.toUInt8() = if (this > 0) {
    UInt8((this + Byte.MIN_VALUE).toByte())
} else { throw ParseException("Negative Int not allowed $this") }
