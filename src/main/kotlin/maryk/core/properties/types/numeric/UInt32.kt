package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

/** Base class for 32 bit/4 byte unsigned integers */
class UInt32 internal constructor(number: Int): UInt<Int>(number) {
    override fun compareTo(other: UInt<Int>) = number.compareTo(other.number)
    override fun toString() = (number.toLong() - Int.MIN_VALUE).toString()
    override fun writeStorageBytes(writer: (Byte) -> Unit) = number.writeBytes(writer)
    companion object : UnsignedNumberDescriptor<UInt32>(
            size = 4,
            MIN_VALUE = UInt32(Int.MIN_VALUE),
            MAX_VALUE = UInt32(Int.MAX_VALUE)
    ) {
        override fun fromStorageByteReader(length: Int, reader: () -> Byte) = UInt32(initInt(reader))
        override fun writeStorageBytes(value: UInt32, writer: (byte: Byte) -> Unit) = value.writeStorageBytes(writer)
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
