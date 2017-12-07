package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
class FlexBytesDefinition(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = true,
        final: Boolean = false,
        unique: Boolean = false,
        minValue: Bytes? = null,
        maxValue: Bytes? = null,
        override val minSize: Int? = null,
        override val maxSize: Int? = null
): AbstractSimpleDefinition<Bytes, IsPropertyContext>(
    indexed, searchable, required, final, WireType.LENGTH_DELIMITED, unique, minValue, maxValue
), HasSizeDefinition, IsSerializableFlexBytesEncodable<Bytes, IsPropertyContext> {
    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(length, reader)

    override fun calculateStorageByteLength(value: Bytes) = value.size

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = value.size

    override fun fromString(string: String) = Bytes.ofBase64String(string)

    override fun validateWithRef(previousValue: Bytes?, newValue: Bytes?, refGetter: () -> IsPropertyReference<Bytes, IsPropertyDefinition<Bytes>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null && (isSizeToSmall(newValue.size) || isSizeToBig(newValue.size))) {
            throw InvalidSizeException(
                    refGetter(), newValue.toHex(), this.minSize, this.maxSize
            )
        }
    }
}