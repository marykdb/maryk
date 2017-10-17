package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.computeVarByteSize
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.extensions.random

/** Base class for 16 bit/2 byte unsigned integers */
class UInt16 internal constructor(number: Short): UInt<Short>(number) {
    override fun compareTo(other: UInt<Short>) = number.compareTo(other.number)
    override fun toString() = (number.toInt() - Short.MIN_VALUE).toString()
    companion object : UnsignedNumberDescriptor<UInt16>(
            size = 2,
            MIN_VALUE = UInt16(Short.MIN_VALUE),
            MAX_VALUE = UInt16(Short.MAX_VALUE)
    ) {
        override fun fromStorageByteReader(length: Int, reader: () -> Byte) = UInt16(initShort(reader))
        override fun writeStorageBytes(value: UInt16, writer: (byte: Byte) -> Unit) = value.number.writeBytes(writer)
        override fun readTransportBytes(reader: () -> Byte) = UInt16((initShortByVar(reader) + Short.MIN_VALUE).toShort())
        override fun writeTransportBytes(value: UInt16, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
            val number = value.number.toInt() - Short.MIN_VALUE
            reserver(number.computeVarByteSize())
            number.writeVarBytes(writer)
        }
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