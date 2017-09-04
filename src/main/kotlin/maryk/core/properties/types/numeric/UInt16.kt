package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.random

/** Base class for 16 bit/2 byte unsigned integers */
class UInt16 internal constructor(number: Short): UInt<Short>(number) {
    override fun compareTo(other: UInt<Short>) = number.compareTo(other.number)
    override fun toString() = (number.toInt() - Short.MIN_VALUE).toString()
    override fun toBytes(bytes: ByteArray?, offset: Int) = number.toBytes(bytes ?: ByteArray(size), offset)
    companion object : UnsignedNumberDescriptor<UInt16>(
            size = 2,
            MIN_VALUE = UInt16(Short.MIN_VALUE),
            MAX_VALUE = UInt16(Short.MAX_VALUE)
    ) {
        override fun toBytes(value: UInt16, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
        override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = UInt16(initShort(bytes, offset))
        override fun ofString(value: String) = UInt16((value.toInt() + Short.MIN_VALUE).toShort())
        override fun createRandom() = UInt16(Short.random())
    }
}

fun Short.toUInt16() = if (this > 0) {
    UInt16((this + Short.MIN_VALUE).toShort())
} else { throw NumberFormatException("Negative Short not allowed $this") }

fun Int.toUInt16() = if (this > 0) {
    UInt16((this + Short.MIN_VALUE).toShort())
} else { throw NumberFormatException("Negative Int not allowed $this") }