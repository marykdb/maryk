package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initByte
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object SInt8 : NumberDescriptor<Byte>(
        size = 1
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Byte = initByte(reader)
    override fun writeStorageBytes(value: Byte, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toByte()
    override fun createRandom() = Byte.random()
}