package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initDouble
import maryk.core.extensions.bytes.writeBytes
import maryk.core.protobuf.WireType
import maryk.lib.extensions.random

object Float64 : NumberDescriptor<Double>(
    size = 8,
    wireType = WireType.BIT_64,
    type = NumberType.Float64
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Double = initDouble(reader)
    override fun writeStorageBytes(value: Double, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initDouble(reader)
    override fun calculateTransportByteLength(value: Double) = this.size
    override fun writeTransportBytes(value: Double, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toDouble()
    override fun ofDouble(value: Double) = value
    override fun ofInt(value: Int) = value.toDouble()
    override fun ofLong(value: Long) = value.toDouble()
    override fun createRandom() = Double.random()
    override fun isOfType(value: Any) = value is Double
}
