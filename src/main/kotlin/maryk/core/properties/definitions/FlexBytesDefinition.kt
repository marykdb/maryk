package maryk.core.properties.definitions

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
data class FlexBytesDefinition(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val unique: Boolean = false,
        override val minValue: Bytes? = null,
        override val maxValue: Bytes? = null,
        override val minSize: Int? = null,
        override val maxSize: Int? = null
): IsComparableDefinition<Bytes, IsPropertyContext>, HasSizeDefinition, IsSerializableFlexBytesEncodable<Bytes, IsPropertyContext> {
    override val wireType = WireType.LENGTH_DELIMITED

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(length, reader)

    override fun calculateStorageByteLength(value: Bytes) = value.size

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = value.size

    override fun fromString(string: String) = Bytes.ofBase64String(string)

    override fun validateWithRef(previousValue: Bytes?, newValue: Bytes?, refGetter: () -> IsPropertyReference<Bytes, IsPropertyDefinition<Bytes>>?) {
        super<IsSerializableFlexBytesEncodable>.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null && (isSizeToSmall(newValue.size) || isSizeToBig(newValue.size))) {
            throw InvalidSizeException(
                    refGetter(), newValue.toHex(), this.minSize, this.maxSize
            )
        }
    }

    companion object : DataModel<FlexBytesDefinition, PropertyDefinitions<FlexBytesDefinition>, IsPropertyContext>(
            properties = object : PropertyDefinitions<FlexBytesDefinition>() {
                init {
                    IsPropertyDefinition.addIndexed(this, FlexBytesDefinition::indexed)
                    IsPropertyDefinition.addSearchable(this, FlexBytesDefinition::searchable)
                    IsPropertyDefinition.addRequired(this, FlexBytesDefinition::required)
                    IsPropertyDefinition.addFinal(this, FlexBytesDefinition::final)
                    IsComparableDefinition.addUnique(this, FlexBytesDefinition::unique)
                    add(5, "minValue", FlexBytesDefinition(), FlexBytesDefinition::minValue)
                    add(6, "maxValue", FlexBytesDefinition(), FlexBytesDefinition::maxValue)
                    HasSizeDefinition.addMinSize(this) { it.minSize?.toUInt32() }
                    HasSizeDefinition.addMaxSize(this) { it.maxSize?.toUInt32() }
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = FlexBytesDefinition(
                indexed = map[0] as Boolean,
                searchable = map[1] as Boolean,
                required = map[2] as Boolean,
                final = map[3] as Boolean,
                unique = map[4] as Boolean,
                minValue = map[5] as Bytes?,
                maxValue = map[6] as Bytes?,
                minSize = (map[7] as UInt32).toInt(),
                maxSize = (map[8] as UInt32).toInt()
        )
    }
}