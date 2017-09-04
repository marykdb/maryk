package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initByte
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.random

object Int8 : NumberDescriptor<Byte>(
        size = 1
) {
    override fun toBytes(value: Byte, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
    override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = initByte(bytes, offset)
    override fun ofString(value: String) = value.toByte()
    override fun createRandom() = Byte.random()
}