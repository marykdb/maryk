package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initFloat
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object Float32 : NumberDescriptor<Float>(
        size = 4
) {
    override fun fromByteReader(length: Int, reader: () -> Byte): Float = initFloat(reader)
    override fun writeBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun toBytes(value: Float, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
    override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = initFloat(bytes, offset)
    override fun ofString(value: String) = value.toFloat()
    override fun createRandom() = Float.random()
}