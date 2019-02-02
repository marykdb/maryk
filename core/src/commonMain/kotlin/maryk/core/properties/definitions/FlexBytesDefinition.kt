package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType
import maryk.core.values.SimpleObjectValues

/** Definition for a bytes array with fixed length */
data class FlexBytesDefinition(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Bytes? = null,
    override val maxValue: Bytes? = null,
    override val default: Bytes? = null,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null
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

    override fun validateWithRef(previousValue: Bytes?, newValue: Bytes?, refGetter: () -> IsPropertyReference<Bytes, IsPropertyDefinition<Bytes>, *>?) {
        super<IsSerializableFlexBytesEncodable>.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null && (isSizeToSmall(newValue.size.toUInt()) || isSizeToBig(newValue.size.toUInt()))) {
            throw InvalidSizeException(
                refGetter(), newValue.toHex(), this.minSize, this.maxSize
            )
        }
    }

    object Model : SimpleObjectDataModel<FlexBytesDefinition, ObjectPropertyDefinitions<FlexBytesDefinition>>(
        properties = object : ObjectPropertyDefinitions<FlexBytesDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, FlexBytesDefinition::indexed)
                IsPropertyDefinition.addRequired(this, FlexBytesDefinition::required)
                IsPropertyDefinition.addFinal(this, FlexBytesDefinition::final)
                IsComparableDefinition.addUnique(this, FlexBytesDefinition::unique)
                add(5, "minValue", FlexBytesDefinition(), FlexBytesDefinition::minValue)
                add(6, "maxValue", FlexBytesDefinition(), FlexBytesDefinition::maxValue)
                add(7, "default", FlexBytesDefinition(), FlexBytesDefinition::default)
                HasSizeDefinition.addMinSize(8, this, FlexBytesDefinition::minSize)
                HasSizeDefinition.addMaxSize(9, this, FlexBytesDefinition::maxSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<FlexBytesDefinition>) = FlexBytesDefinition(
            indexed = values(1),
            required = values(2),
            final = values(3),
            unique = values(4),
            minValue = values(5),
            maxValue = values(6),
            default = values(7),
            minSize = values(8),
            maxSize = values(9)
        )
    }
}
