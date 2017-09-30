package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initDouble
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object Float64 : NumberDescriptor<Double>(
        size = 8
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Double = initDouble(reader)
    override fun writeStorageBytes(value: Double, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toDouble()
    override fun createRandom() = Double.random()
}