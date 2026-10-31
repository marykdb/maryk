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
    override fun sum(value1: Float, value2: Float) = (value1.atFloat32Precision() + value2.atFloat32Precision()).atFloat32Precision()
    override fun divide(value1: Float, value2: Float) = (value1.atFloat32Precision() / value2.atFloat32Precision()).atFloat32Precision()
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Float {
        checkStorageByteLength(length)
        return initFloat(reader)
    }
    override fun writeStorageBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initFloatFromTransport(reader)
    override fun calculateTransportByteLength(value: Float) = size
    override fun writeTransportBytes(value: Float, writer: (byte: Byte) -> Unit) = value.writeTransportBytes(writer)
    override fun ofString(value: String) = parseFloat32Text(value)
    override fun ofDouble(double: Double) = double.toFloat().atFloat32Precision()
    override fun toDouble(value: Float) = value.atFloat32Precision().toDouble()
    override fun ofInt(int: Int) = int.toFloat().atFloat32Precision()
    override fun ofLong(long: Long) = parseFloat32Text(long.toString())
    override fun createRandom() = Random.nextFloat().atFloat32Precision()
    override fun isOfType(value: Any) = value is Float
}

/** JS uses Number for Float; force the same rounding as the persisted Float32 bits. */
internal fun Float.atFloat32Precision() = Float.fromBits(toRawBits())
