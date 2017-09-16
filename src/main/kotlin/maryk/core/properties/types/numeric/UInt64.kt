package maryk.core.properties.types

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.initByteArrayByHex
import maryk.core.extensions.random
import maryk.core.extensions.toHex
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.types.numeric.UInt
import maryk.core.properties.types.numeric.UnsignedNumberDescriptor

/** Base class for 64 bit/8 byte unsigned integers */
class UInt64 internal constructor(number: Long): UInt<Long>(number) {
    override fun compareTo(other: UInt<Long>) = number.compareTo(other.number)
    override fun toString(): String {
        val bytes = ByteArray(8)
        var index = 0
        number.writeBytes({ bytes[index++] = it })
        return "0x${bytes.toHex()}"
    }
    override fun writeBytes(writer: (Byte) -> Unit) = number.writeBytes(writer)
    companion object : UnsignedNumberDescriptor<UInt64>(
            size = 8,
            MIN_VALUE = UInt64(Long.MIN_VALUE),
            MAX_VALUE = UInt64(Long.MAX_VALUE)
    ) {
        override fun fromByteReader(length: Int, reader: () -> Byte) = UInt64(initLong(reader))
        override fun writeBytes(value: UInt64, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
        override fun ofString(value: String): UInt64 {
            if(value.startsWith("0x") && value.length < 4) { throw ParseException("Long should be represented by hex") }
            val bytes = initByteArrayByHex(value.substring(2))
            var index = 0
            return UInt64(initLong({ bytes[index++] }))
        }
        override fun createRandom() = UInt64(Long.random())
    }
}

fun Long.toUInt64() = if (this > 0) {
    UInt64(this + Long.MIN_VALUE)
} else { throw NumberFormatException("Negative Long not allowed $this") }