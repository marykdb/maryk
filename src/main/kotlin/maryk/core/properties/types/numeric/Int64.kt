package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initLong
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.random

object Int64 : NumberDescriptor<Long>(
        size = 8
) {
    override fun toBytes(value: Long, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
    override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = initLong(bytes, offset)
    override fun ofString(value: String) = value.toLong()
    override fun createRandom() = Long.random()
}