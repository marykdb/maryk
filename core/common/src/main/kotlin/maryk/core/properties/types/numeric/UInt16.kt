package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.lib.exceptions.ParseException

/** Base class for 16 bit/2 byte unsigned integers */
class UInt16 internal constructor(number: Short): UnsignedInt<Short>(number) {
    override fun compareTo(other: UnsignedInt<Short>) = number.compareTo(other.number)
    override fun toString() = (number.toInt() - Short.MIN_VALUE).toString()
    override fun toInt() = this.number - Short.MIN_VALUE
    override fun toLong() = number.toLong() - Byte.MIN_VALUE

    companion object : UnsignedNumberDescriptor<UInt16>(
        size = 2,
        MIN_VALUE = UInt16(Short.MIN_VALUE),
        MAX_VALUE = UInt16(Short.MAX_VALUE),
        type = NumberType.UInt16
    ) {
        override fun fromStorageByteReader(length: Int, reader: () -> Byte) = UInt16(initShort(reader))
        override fun writeStorageBytes(value: UInt16, writer: (byte: Byte) -> Unit) = value.number.writeBytes(writer)
        override fun readTransportBytes(reader: () -> Byte) = UInt16((initShortByVar(reader) + Short.MIN_VALUE).toShort())
        override fun calculateTransportByteLength(value: UInt16) = (value.number - Short.MIN_VALUE).calculateVarByteLength()
        override fun writeTransportBytes(value: UInt16, writer: (byte: Byte) -> Unit) {
            val number = value.number.toInt() - Short.MIN_VALUE
            number.writeVarBytes(writer)
        }
        override fun ofString(value: String) = UInt16((value.toInt() + Short.MIN_VALUE).toShort())
        override fun ofDouble(value: Double) = value.toInt().toUInt16()
        override fun ofInt(value: Int) = value.toUInt16()
        override fun ofLong(value: Long) = value.toInt().toUInt16()
        override fun createRandom() = UInt16(SInt16.createRandom())
        override fun isOfType(value: Any) = value == UInt16
    }
}

fun Short.toUInt16() = if (this >= 0) {
    UInt16((this + Short.MIN_VALUE).toShort())
} else { throw ParseException("Negative Short not allowed $this") }

fun Int.toUInt16() = if (this >= 0) {
    UInt16((this + Short.MIN_VALUE).toShort())
} else { throw ParseException("Negative Int not allowed $this") }
