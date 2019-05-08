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
    type = UInt16Type
) {
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
    override fun ofDouble(value: Double) = value.toInt().toUShort()
    override fun toDouble(value: UShort) = value.toLong().toDouble()
    override fun ofInt(value: Int) = value.toUShort()
    override fun ofLong(value: Long) = value.toUShort()
    override fun createRandom() = Random.nextInt(UShort.MAX_VALUE.toInt()).toUShort()
    override fun isOfType(value: Any) = value == UShort
}
