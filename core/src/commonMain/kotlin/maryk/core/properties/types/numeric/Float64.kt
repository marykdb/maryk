package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initDouble
import maryk.core.extensions.bytes.initDoubleFromTransport
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeTransportableBytes
import maryk.core.properties.types.numeric.NumberType.Float64Type
import maryk.core.protobuf.WireType.BIT_64
import kotlin.random.Random

object Float64 : NumberDescriptor<Double>(
    size = 8,
    wireType = BIT_64,
    type = Float64Type,
    zero = 0.0
) {
    override fun sum(value1: Double, value2: Double) = value1 + value2
    override fun divide(value1: Double, value2: Double) = value1 / value2
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Double = initDouble(reader)
    override fun writeStorageBytes(value: Double, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initDoubleFromTransport(reader)
    override fun calculateTransportByteLength(value: Double) = this.size
    override fun writeTransportBytes(value: Double, writer: (byte: Byte) -> Unit) =
        value.writeTransportableBytes(writer)

    override fun ofString(value: String) = value.toDouble()
    override fun ofDouble(double: Double) = double
    override fun toDouble(value: Double) = value
    override fun ofInt(int: Int) = int.toDouble()
    override fun ofLong(long: Long) = long.toDouble()
    override fun createRandom() = Random.nextDouble()
    override fun isOfType(value: Any) = value is Double
}
