package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.lib.exceptions.ParseException
import maryk.lib.extensions.initByteArrayByHex
import maryk.lib.extensions.random
import maryk.lib.extensions.toHex

/** Base class for 64 bit/8 byte unsigned integers */
class UInt64 internal constructor(number: Long): UInt<Long>(number) {
    override fun compareTo(other: UInt<Long>) = number.compareTo(other.number)

    override fun toString(): String {
        // If number is within normal positive Long range. Print it as base 10
        return if (this.number < 0L) {
            this.toLong().toString()
        } else {
            val bytes = ByteArray(8)
            var index = 0
            number.writeBytes({ bytes[index++] = it })
            return "0x${bytes.toHex(true)}"
        }
    }

    override fun toInt() = (this.number - Long.MIN_VALUE).toInt()
    override fun toLong() = this.number + Long.MIN_VALUE

    companion object : UnsignedNumberDescriptor<UInt64>(
        size = 8,
        MIN_VALUE = UInt64(Long.MIN_VALUE),
        MAX_VALUE = UInt64(Long.MAX_VALUE),
        type = NumberType.UInt64
    ) {
        override fun fromStorageByteReader(length: Int, reader: () -> Byte) = UInt64(initLong(reader))
        override fun writeStorageBytes(value: UInt64, writer: (byte: Byte) -> Unit) = value.number.writeBytes(writer)
        override fun readTransportBytes(reader: () -> Byte) = UInt64(initLongByVar(reader) + Long.MIN_VALUE)
        override fun calculateTransportByteLength(value: UInt64) = (value.number - Long.MIN_VALUE).calculateVarByteLength()
        override fun writeTransportBytes(value: UInt64, writer: (byte: Byte) -> Unit) {
            val number = value.number - Long.MIN_VALUE
            number.writeVarBytes(writer)
        }
        override fun ofString(value: String): UInt64 {
            return if(value.startsWith("0x")) {
                if (value.length < 4) {
                    throw ParseException("Hex string should be at least 4 characters long")
                }
                val bytes = initByteArrayByHex(value.substring(2))
                var index = 0
                UInt64(initLong({ bytes[index++] }))
            } else if(value.startsWith("-")) {
                throw ParseException("UInt64 cannot start with a -")
            } else {
                value.toLong().toUInt64()
            }
        }
        override fun ofDouble(value: Double) = value.toLong().toUInt64()
        override fun ofInt(value: Int) = value.toLong().toUInt64()
        override fun ofLong(value: Long) = value.toUInt64()
        override fun createRandom() = UInt64(Long.random())
        override fun isOfType(value: Any) = value == UInt64
    }
}

fun Long.toUInt64() = if (this >= 0) {
    UInt64(this + Long.MIN_VALUE)
} else { throw ParseException("Negative Long not allowed $this") }
