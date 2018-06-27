package maryk.core.properties.definitions

import maryk.core.models.SimpleDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
data class FlexBytesDefinition(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Bytes? = null,
    override val maxValue: Bytes? = null,
    override val default: Bytes? = null,
    override val minSize: Int? = null,
    override val maxSize: Int? = null
):
    IsComparableDefinition<Bytes, IsPropertyContext>,
    HasSizeDefinition,
    IsSerializableFlexBytesEncodable<Bytes, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Bytes>,
    HasDefaultValueDefinition<Bytes>
{
    override val propertyDefinitionType = PropertyDefinitionType.FlexBytes
    override val wireType = WireType.LENGTH_DELIMITED

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(length, reader)

    override fun calculateStorageByteLength(value: Bytes) = value.size

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = value.size

    override fun fromString(string: String) = Bytes(string)

    override fun fromNativeType(value: Any) =
        if(value is ByteArray){
            Bytes(value)
        } else {
            value as? Bytes
        }

    override fun validateWithRef(previousValue: Bytes?, newValue: Bytes?, refGetter: () -> IsPropertyReference<Bytes, IsPropertyDefinition<Bytes>>?) {
        super<IsSerializableFlexBytesEncodable>.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null && (isSizeToSmall(newValue.size) || isSizeToBig(newValue.size))) {
            throw InvalidSizeException(
                refGetter(), newValue.toHex(), this.minSize, this.maxSize
            )
        }
    }

    object Model : SimpleDataModel<FlexBytesDefinition, PropertyDefinitions<FlexBytesDefinition>>(
        properties = object : PropertyDefinitions<FlexBytesDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, FlexBytesDefinition::indexed)
                IsPropertyDefinition.addRequired(this, FlexBytesDefinition::required)
                IsPropertyDefinition.addFinal(this, FlexBytesDefinition::final)
                IsComparableDefinition.addUnique(this, FlexBytesDefinition::unique)
                add(4, "minValue", FlexBytesDefinition(), FlexBytesDefinition::minValue)
                add(5, "maxValue", FlexBytesDefinition(), FlexBytesDefinition::maxValue)
                add(6, "default", FlexBytesDefinition(), FlexBytesDefinition::default)
                HasSizeDefinition.addMinSize(7, this, FlexBytesDefinition::minSize)
                HasSizeDefinition.addMaxSize(8, this, FlexBytesDefinition::maxSize)
            }
        }
    ) {
        override fun invoke(map: DataObjectMap<FlexBytesDefinition>) = FlexBytesDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            unique = map(3),
            minValue = map(4),
            maxValue = map(5),
            default = map(6),
            minSize = map(7),
            maxSize = map(8)
        )
    }
}
