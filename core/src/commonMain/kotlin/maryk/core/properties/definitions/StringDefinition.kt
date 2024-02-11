package maryk.core.properties.definitions

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleObjectModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.DefinitionWrapperDelegateLoader
import maryk.core.properties.definitions.wrapper.FlexBytesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.ObjectDefinitionWrapperDelegateLoader
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.values.SimpleObjectValues
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.bytes.initString
import maryk.lib.bytes.writeUTF8Bytes

/** Definition for String properties */
data class StringDefinition(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: String? = null,
    override val maxValue: String? = null,
    override val default: String? = null,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    val regEx: String? = null
) :
    IsComparableDefinition<String, IsPropertyContext>,
    HasSizeDefinition,
    IsSerializableFlexBytesEncodable<String, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<String>,
    HasDefaultValueDefinition<String> {
    override val propertyDefinitionType = PropertyDefinitionType.String
    override val wireType = LENGTH_DELIMITED

    private val _regEx by lazy {
        when {
            this.regEx != null -> Regex(this.regEx)
            else -> null
        }
    }

    override fun readStorageBytes(length: Int, reader: () -> Byte) = initString(length, reader)

    override fun calculateStorageByteLength(value: String) = value.calculateUTF8ByteLength()

    override fun writeStorageBytes(value: String, writer: (byte: Byte) -> Unit) = value.writeUTF8Bytes(writer)

    override fun calculateTransportByteLength(value: String) = value.calculateUTF8ByteLength()

    override fun asString(value: String) = value

    override fun fromString(string: String) = string

    override fun fromNativeType(value: Any) = value as? String

    override fun validateWithRef(
        previousValue: String?,
        newValue: String?,
        refGetter: () -> IsPropertyReference<String, IsPropertyDefinition<String>, *>?
    ) {
        super<IsComparableDefinition>.validateWithRef(previousValue, newValue, refGetter)

        when {
            newValue != null -> {
                val length = newValue.length.toUInt()
                when {
                    isSizeToSmall(length) || isSizeToBig(length)
                    -> throw InvalidSizeException(
                        refGetter(), newValue, this.minSize, this.maxSize
                    )
                    this._regEx?.let {
                        !(it matches newValue)
                    } ?: false
                    -> throw InvalidValueException(
                        refGetter(), newValue
                    )
                }
            }
        }
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        checkedDataModelNames: MutableList<String>?,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super<IsComparableDefinition>.compatibleWith(definition, checkedDataModelNames, addIncompatibilityReason)
        if (definition is StringDefinition) {
            if (regEx != null && regEx != definition.regEx) {
                addIncompatibilityReason?.invoke("Regular expression ($$regEx) cannot be added or changed.")
                compatible = false
            }

            compatible = this.isCompatible(definition, addIncompatibilityReason) && compatible
        }
        return compatible
    }

    object Model : SimpleObjectModel<StringDefinition, IsObjectDataModel<StringDefinition>>() {
        val required by boolean(1u, StringDefinition::required, default = true)
        val final by boolean(2u, StringDefinition::final, default = false)
        val unique by boolean(3u, StringDefinition::unique, default = false)
        val minValue by string(4u, StringDefinition::minValue)
        val maxValue by string(5u, StringDefinition::maxValue)
        val default by string(6u, StringDefinition::default)
        val minSize by number(7u, StringDefinition::minSize, type = UInt32)
        val maxSize by number(8u, StringDefinition::maxSize, type = UInt32)
        val regEx by string(9u, StringDefinition::regEx)

        override fun invoke(values: SimpleObjectValues<StringDefinition>) = StringDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u),
            minSize = values(7u),
            maxSize = values(8u),
            regEx = values(9u)
        )
    }
}

fun IsValuesDataModel.string(
    index: UInt,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: String? = null,
    maxValue: String? = null,
    default: String? = null,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    regEx: String? = null,
    alternativeNames: Set<String>? = null
) = DefinitionWrapperDelegateLoader(this) { propName ->
    FlexBytesDefinitionWrapper<String, String, IsPropertyContext, StringDefinition, Any>(
        index,
        name ?: propName,
        StringDefinition(required, final, unique, minValue, maxValue, default, minSize, maxSize, regEx),
        alternativeNames
    )
}

fun <TO: Any, DO: Any> IsObjectDataModel<DO>.string(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: String? = null,
    maxValue: String? = null,
    default: String? = null,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    regEx: String? = null,
    alternativeNames: Set<String>? = null
): ObjectDefinitionWrapperDelegateLoader<FlexBytesDefinitionWrapper<String, TO, IsPropertyContext, StringDefinition, DO>, DO, IsPropertyContext> =
    string(index, getter, name, required, final,  unique, minValue, maxValue, default, minSize, maxSize, regEx, alternativeNames, toSerializable = null)

fun <TO: Any, DO: Any, CX: IsPropertyContext> IsObjectDataModel<DO>.string(
    index: UInt,
    getter: (DO) -> TO?,
    name: String? = null,
    required: Boolean = true,
    final: Boolean = false,
    unique: Boolean = false,
    minValue: String? = null,
    maxValue: String? = null,
    default: String? = null,
    minSize: UInt? = null,
    maxSize: UInt? = null,
    regEx: String? = null,
    alternativeNames: Set<String>? = null,
    toSerializable: (Unit.(TO?, CX?) -> String?)? = null,
    fromSerializable: (Unit.(String?) -> TO?)? = null,
    shouldSerialize: (Unit.(Any) -> Boolean)? = null,
    capturer: (Unit.(CX, String) -> Unit)? = null
) = ObjectDefinitionWrapperDelegateLoader(this) { propName ->
    FlexBytesDefinitionWrapper(
        index,
        name ?: propName,
        StringDefinition(required, final, unique, minValue, maxValue, default, minSize, maxSize, regEx),
        alternativeNames,
        getter = getter,
        capturer = capturer,
        toSerializable = toSerializable,
        fromSerializable = fromSerializable,
        shouldSerialize = shouldSerialize
    )
}
