package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.random

/** Base class for 32 bit/4 byte unsigned integers */
class UInt32 internal constructor(number: Int): UInt<Int>(number) {
    override fun compareTo(other: UInt<Int>) = number.compareTo(other.number)
    override fun toString() = (number.toLong() - Int.MIN_VALUE).toString()
    override fun toBytes(bytes: ByteArray?, offset: Int) = number.toBytes(bytes ?: ByteArray(size), offset)
    companion object : UnsignedNumberDescriptor<UInt32>(
            size = 4,
            MIN_VALUE = UInt32(Int.MIN_VALUE),
            MAX_VALUE = UInt32(Int.MAX_VALUE)
    ) {
        override fun toBytes(value: UInt32, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
        override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = UInt32(initInt(bytes, offset, length))
        override fun ofString(value: String) = UInt32((value.toLong() + Int.MIN_VALUE).toInt())
        override fun createRandom() = UInt32(Int.random())
    }
}

fun Int.toUInt32() = if (this > 0) {
    UInt32(this + Int.MIN_VALUE)
} else { throw NumberFormatException("Negative Int not allowed $this") }

fun Long.toUInt32() = if (this > 0) {
    UInt32((this + Int.MIN_VALUE).toInt())
} else { throw NumberFormatException("Negative Long not allowed $this") }
