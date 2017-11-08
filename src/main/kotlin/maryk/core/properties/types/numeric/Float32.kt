package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initFloat
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random
import maryk.core.protobuf.WireType

object Float32 : NumberDescriptor<Float>(
        size = 4,
        wireType = WireType.BIT_32
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Float = initFloat(reader)
    override fun writeStorageBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initFloat(reader)
    override fun calculateTransportByteLength(value: Float) = size
    override fun writeTransportBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toFloat()
    override fun createRandom() = Float.random()
}