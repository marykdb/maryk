package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.decodeZigZag
import maryk.core.extensions.bytes.encodeZigZag
import maryk.core.extensions.bytes.initInt
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.protobuf.WireType
import kotlin.random.Random

object SInt32 : NumberDescriptor<Int>(
    size = 4,
    wireType = WireType.VAR_INT,
    type = NumberType.SInt32
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Int = initInt(reader)
    override fun writeStorageBytes(value: Int, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun readTransportBytes(reader: () -> Byte) = initIntByVar(reader).decodeZigZag()
    override fun calculateTransportByteLength(value: Int) = value.encodeZigZag().calculateVarByteLength()
    override fun writeTransportBytes(value: Int, writer: (byte: Byte) -> Unit) {
        val zigZaggedValue = value.encodeZigZag()
        zigZaggedValue.writeVarBytes(writer)
    }
    override fun ofString(value: String) = value.toInt()
    override fun ofDouble(value: Double) = value.toInt()
    override fun ofInt(value: Int) = value
    override fun ofLong(value: Long) = value.toInt()
    override fun createRandom() = Random.nextInt()
    override fun isOfType(value: Any) = value is Int
}
