package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object Int64 : NumberDescriptor<Long>(
        size = 8
) {
    override fun fromByteReader(length: Int, reader: () -> Byte): Long = initLong(reader)
    override fun writeBytes(value: Long, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toLong()
    override fun createRandom() = Long.random()
}