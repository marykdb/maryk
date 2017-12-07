package maryk.core.properties.definitions

import maryk.core.extensions.randomBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
class FixedBytesDefinition(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = true,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Bytes? = null,
        maxValue: Bytes? = null,
        random: Boolean = false,
        override val byteSize: Int
): AbstractNumericDefinition<Bytes>(
    indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue, random
), IsSerializableFixedBytesEncodable<Bytes, IsPropertyContext> {
    override fun createRandom() = Bytes(randomBytes(this.byteSize))

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun calculateStorageByteLength(value: Bytes) = this.byteSize

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = this.byteSize

    override fun fromString(string: String) = Bytes.ofBase64String(string)
}