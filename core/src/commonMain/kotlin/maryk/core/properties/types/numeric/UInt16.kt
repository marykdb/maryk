package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.initShortByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.types.numeric.NumberType.UInt16Type
import kotlin.random.Random

/** Object for 16 bit/2 byte unsigned integers */
object UInt16 : UnsignedNumberDescriptor<UShort>(
    size = 2,
    MIN_VALUE = UShort.MIN_VALUE,
    MAX_VALUE = UShort.MAX_VALUE,
    type = UInt16Type,
    zero = 0.toUShort()
) {
    override fun sum(value1: UShort, value2: UShort) = (value1 + value2).toUShort()
    override fun divide(value1: UShort, value2: UShort) = (value1 / value2).toUShort()
    override fun fromStorageByteReader(length: Int, reader: () -> Byte) =
        (initShort(reader) + Short.MIN_VALUE).toUShort()

    override fun writeStorageBytes(value: UShort, writer: (byte: Byte) -> Unit) =
        (value.toShort() - Short.MIN_VALUE).toShort().writeBytes(writer)

    override fun readTransportBytes(reader: () -> Byte) = initShortByVar(reader).toUShort()
    override fun calculateTransportByteLength(value: UShort) = value.toInt().calculateVarByteLength()
    override fun writeTransportBytes(value: UShort, writer: (byte: Byte) -> Unit) {
        value.toInt().writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toUShort()
    override fun ofDouble(double: Double) = double.toInt().toUShort()
    override fun toDouble(value: UShort) = value.toLong().toDouble()
    override fun ofInt(int: Int) = int.toUShort()
    override fun ofLong(long: Long) = long.toUShort()
    override fun createRandom() = Random.nextInt(UShort.MAX_VALUE.toInt() + 1).toUShort()
    override fun isOfType(value: Any) = value is UShort
}
