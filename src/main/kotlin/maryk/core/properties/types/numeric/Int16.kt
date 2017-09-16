package maryk.core.properties.types.numeric

import maryk.core.extensions.bytes.initShort
import maryk.core.extensions.bytes.toBytes
import maryk.core.extensions.bytes.writeBytes
import maryk.core.extensions.random

object Int16 : NumberDescriptor<Short>(
        size = 2
) {
    override fun fromByteReader(length: Int, reader: () -> Byte): Short = initShort(reader)
    override fun writeBytes(value: Short, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)
    override fun toBytes(value: Short, bytes: ByteArray?, offset: Int) = value.toBytes(bytes, offset)
    override fun ofBytes(bytes: ByteArray, offset: Int, length: Int) = initShort(bytes, offset)
    override fun ofString(value: String) = value.toShort()
    override fun createRandom() = Short.random()
}