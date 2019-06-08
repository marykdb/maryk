package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initFloat
import maryk.core.extensions.bytes.initFloatFromTransport
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeTransportBytes
import maryk.core.properties.types.numeric.NumberType.Float32Type
import maryk.core.protobuf.WireType.BIT_32
import kotlin.random.Random

object Float32 : NumberDescriptor<Float>(
    size = 4,
    wireType = BIT_32,
    type = Float32Type,
    zero = 0.toFloat()
) {
    override fun sum(value1: Float, value2: Float) = value1 + value2
    override fun divide(value1: Float, value2: Float) = value1 / value2
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Float = initFloat(reader)
    override fun writeStorageBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initFloatFromTransport(reader)
    override fun calculateTransportByteLength(value: Float) = size
    override fun writeTransportBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeTransportBytes(writer)
    override fun ofString(value: String) = value.toFloat()
    override fun ofDouble(double: Double) = double.toFloat()
    override fun toDouble(value: Float) = value.toDouble()
    override fun ofInt(int: Int) = int.toFloat()
    override fun ofLong(long: Long) = long.toFloat()
    override fun createRandom() = Random.nextFloat()
    override fun isOfType(value: Any) = value is Float
}
