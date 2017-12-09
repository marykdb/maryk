package maryk.core.properties.definitions

import maryk.core.extensions.randomBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
data class FixedBytesDefinition(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val unique: Boolean = false,
        override val minValue: Bytes? = null,
        override val maxValue: Bytes? = null,
        override val random: Boolean = false,
        override val byteSize: Int
): IsNumericDefinition<Bytes>, IsSerializableFixedBytesEncodable<Bytes, IsPropertyContext> {
    override val wireType = WireType.LENGTH_DELIMITED

    override fun createRandom() = Bytes(randomBytes(this.byteSize))

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun calculateStorageByteLength(value: Bytes) = this.byteSize

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = this.byteSize

    override fun fromString(string: String) = Bytes.ofBase64String(string)
}