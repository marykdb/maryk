package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionType.FlexBytes
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.UInt32
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
    HasDefaultValueDefinition<Bytes> {
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

    @Suppress("unused")
    object Model : SimpleObjectDataModel<FlexBytesDefinition, ObjectPropertyDefinitions<FlexBytesDefinition>>(
        properties = object : ObjectPropertyDefinitions<FlexBytesDefinition>() {
            val required by boolean(1u, FlexBytesDefinition::required, default = true)
            val final by boolean(2u, FlexBytesDefinition::final, default = false)
            val unique by boolean(3u, FlexBytesDefinition::unique, default = false)
            val minValue by flexBytes(4u, FlexBytesDefinition::minValue)
            val maxValue by flexBytes(5u, FlexBytesDefinition::maxValue)
            val default by flexBytes(6u, FlexBytesDefinition::default)
            val minSize by number(7u, FlexBytesDefinition::minSize, type = UInt32)
            val maxSize by number(8u, FlexBytesDefinition::maxSize, type = UInt32)
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

fun PropertyDefinitions.flexBytes(
    index: UInt,
    required: Boolean = true,
    name: String? = null,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Bytes? = null,
    maxValue: Bytes? = null,
    default: Bytes? = null,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FlexBytesDefinitionWrapper<Bytes, Bytes, IsPropertyContext, FlexBytesDefinition, Any>(
        index,
        name ?: propName,
        FlexBytesDefinition(required, final, unique, minValue, maxValue, default, minSize, maxSize),
        alternativeNames
    )
}

fun <TO: Any, DO: Any> ObjectPropertyDefinitions<DO>.flexBytes(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Bytes? = null,
    maxValue: Bytes? = null,
    default: Bytes? = null,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FlexBytesDefinitionWrapper<Bytes, TO, IsPropertyContext, FlexBytesDefinition, DO>, DO> =
    flexBytes(index, getter, name, required, final,  unique, minValue, maxValue, default, minSize, maxSize, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> ObjectPropertyDefinitions<DO>.flexBytes(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: Bytes? = null,
    maxValue: Bytes? = null,
    default: Bytes? = null,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> Bytes?)? = null,
    fromSerializable: (Unit.(Bytes?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, Bytes) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FlexBytesDefinitionWrapper(
        index,
        name ?: propName,
        FlexBytesDefinition(required, final, unique, minValue, maxValue, default, minSize, maxSize),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
