package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initDouble
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object Float64 : NumberDescriptor<Double>(
        size = 8
) {
    override fun fromByteReader(length: Int, reader: () -> Byte): Double = initDouble(reader)
    override fun writeBytes(value: Double, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun toBytes(value: Double, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
    override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = initDouble(bytes, offset)
    override fun ofString(value: String) = value.toDouble()
    override fun createRandom() = Double.random()
}