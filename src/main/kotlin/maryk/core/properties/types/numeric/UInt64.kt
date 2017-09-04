package maryk.core.properties.types

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.initByteArrayByHex
import maryk.core.extensions.random
import maryk.core.extensions.toHex
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.UInt
import maryk.core.properties.types.numeric.UnsignedNumberDescriptor

/** Base class for 64 bit/8 byte unsigned integers */
class UInt64 internal constructor(number: Long): UInt<Long>(number) {
    override fun compareTo(other: UInt<Long>) = number.compareTo(other.number)
    override fun toString() = "0x${number.toBytes().toHex()}"
    override fun toBytes(bytes: ByteArray?, offset: Int) = number.toBytes(bytes ?: ByteArray(size), offset)
    companion object : UnsignedNumberDescriptor<UInt64>(
            size = 8,
            MIN_VALUE = UInt64(Long.MIN_VALUE),
            MAX_VALUE = UInt64(Long.MAX_VALUE)
    ) {
        override fun toBytes(value: UInt64, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
        override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = UInt64(initLong(bytes, offset, length))
        override fun ofString(value: String): UInt64 {
            if(value.startsWith("0x") && value.length < 4) { throw ParseException("Long should be represented by hex")
            }
            return UInt64(initLong(initByteArrayByHex(value.substring(2))))
        }
        override fun createRandom() = UInt64(Long.random())
    }
}

fun Long.toUInt64() = if (this > 0) {
    UInt64(this + Long.MIN_VALUE)
} else { throw NumberFormatException("Negative Long not allowed $this") }