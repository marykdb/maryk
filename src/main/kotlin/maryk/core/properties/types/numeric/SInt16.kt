package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object SInt16 : NumberDescriptor<Short>(
        size = 2
) {
    override fun fromStorageByteReader(length: Int, reader: () -> Byte): Short = initShort(reader)
    override fun writeStorageBytes(value: Short, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun ofString(value: String) = value.toShort()
    override fun createRandom() = Short.random()
}