package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initFloat
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object Float32 : NumberDescriptor<Float>(
        size = 4
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Float = initFloat(reader)
    override fun writeStorageBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toFloat()
    override fun createRandom() = Float.random()
}