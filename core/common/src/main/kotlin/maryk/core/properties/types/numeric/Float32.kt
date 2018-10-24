package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initFloat
import maryk.core.extensions.bytes.initFloatFromTransport
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeTransportBytes
import maryk.core.protobuf.WireType
import kotlin.random.Random

object Float32 : NumberDescriptor<Float>(
    size = 4,
    wireType = WireType.BIT_32,
    type = NumberType.Float32
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Float = initFloat(reader)
    override fun writeStorageBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initFloatFromTransport(reader)
    override fun calculateTransportByteLength(value: Float) = size
    override fun writeTransportBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeTransportBytes(writer)
    override fun ofString(value: String) = value.toFloat()
    override fun ofDouble(value: Double) = value.toFloat()
    override fun ofInt(value: Int) = value.toFloat()
    override fun ofLong(value: Long) = value.toFloat()
    override fun createRandom() = Random.nextFloat()
    override fun isOfType(value: Any) = value is Float
}
