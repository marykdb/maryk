package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.initLongByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.protobuf.WireType.VAR_INT
import kotlin.random.Random

object SInt64 : NumberDescriptor<Long>(
    size = 8,
    wireType = VAR_INT,
    type = NumberType.SInt64
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Long = initLong(reader)
    override fun writeStorageBytes(value: Long, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initLongByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Long) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Long, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }

    override fun ofString(value: String) = value.toLong()
    override fun ofDouble(value: Double) = value.toLong()
    override fun toDouble(value: Long) = value.toDouble()
    override fun ofInt(value: Int) = value.toLong()
    override fun ofLong(value: Long) = value
    override fun createRandom() = Random.nextLong()
    override fun isOfType(value: Any) = value is Long
}
