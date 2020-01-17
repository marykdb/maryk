package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionType.FlexBytes
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.values.SimpleObjectValues

/** Definition for a bytes array with fixed length */
data class FlexBytesDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Bytes? = null,
    override val maxValue: Bytes? = null,
    override val default: Bytes? = null,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null
) :
    IsComparableDefinition<Bytes, IsPropertyContext>,
    HasSizeDefinition,
    IsSerializableFlexBytesEncodable<Bytes, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Bytes>,
    HasDefaultValueDefinition<Bytes>,
    IsWrappableDefinition<Bytes, IsPropertyContext, FlexBytesDefinitionWrapper<Bytes, Bytes, IsPropertyContext, FlexBytesDefinition, Any>> {
    override val propertyDefinitionType = FlexBytes
    override val wireType = LENGTH_DELIMITED

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(length, reader)

    override fun calculateStorageByteLength(value: Bytes) = value.size

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = value.size

    override fun fromString(string: String) = Bytes(string)

    override fun fromNativeType(value: Any) =
        if (value is ByteArray) {
            Bytes(value)
        } else {
            value as? Bytes
        }

    override fun validateWithRef(
        previousValue: Bytes?,
        newValue: Bytes?,
        refGetter: () -> IsPropertyReference<Bytes, IsPropertyDefinition<Bytes>, *>?
    ) {
        super<IsSerializableFlexBytesEncodable>.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null && (isSizeToSmall(newValue.size.toUInt()) || isSizeToBig(newValue.size.toUInt()))) {
            throw InvalidSizeException(
                refGetter(), newValue.toHex(), this.minSize, this.maxSize
            )
        }
    }

    override fun wrap(
        index: UInt,
        name: String,
        alternativeNames: Set<String>?
    ) =
        FlexBytesDefinitionWrapper<Bytes, Bytes, IsPropertyContext, FlexBytesDefinition, Any>(index, name, this, alternativeNames)


    object Model : SimpleObjectDataModel<FlexBytesDefinition, ObjectPropertyDefinitions<FlexBytesDefinition>>(
        properties = object : ObjectPropertyDefinitions<FlexBytesDefinition>() {
            init {
                IsPropertyDefinition.addRequired(this, FlexBytesDefinition::required)
                IsPropertyDefinition.addFinal(this, FlexBytesDefinition::final)
                IsComparableDefinition.addUnique(this, FlexBytesDefinition::unique)
                add(4u, "minValue", FlexBytesDefinition(), FlexBytesDefinition::minValue)
                add(5u, "maxValue", FlexBytesDefinition(), FlexBytesDefinition::maxValue)
                add(6u, "default", FlexBytesDefinition(), FlexBytesDefinition::default)
                HasSizeDefinition.addMinSize(7u, this, FlexBytesDefinition::minSize)
                HasSizeDefinition.addMaxSize(8u, this, FlexBytesDefinition::maxSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<FlexBytesDefinition>) = FlexBytesDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u),
            minSize = values(7u),
            maxSize = values(8u)
        )
    }
}
