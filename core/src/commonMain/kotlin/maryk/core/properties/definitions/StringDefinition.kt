package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType
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
    override val wireType = WireType.LENGTH_DELIMITED

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

    object Model : SimpleObjectDataModel<StringDefinition, ObjectPropertyDefinitions<StringDefinition>>(
        properties = object : ObjectPropertyDefinitions<StringDefinition>() {
            init {
                IsPropertyDefinition.addRequired(this, StringDefinition::required)
                IsPropertyDefinition.addFinal(this, StringDefinition::final)
                IsComparableDefinition.addUnique(this, StringDefinition::unique)
                add(4, "minValue", StringDefinition(), StringDefinition::minValue)
                add(5, "maxValue", StringDefinition(), StringDefinition::maxValue)
                add(6, "default", StringDefinition(), StringDefinition::default)
                HasSizeDefinition.addMinSize(7, this, StringDefinition::minSize)
                HasSizeDefinition.addMaxSize(8, this, StringDefinition::maxSize)
                add(9, "regEx", StringDefinition(), StringDefinition::regEx)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<StringDefinition>) = StringDefinition(
            required = values(1),
            final = values(2),
            unique = values(3),
            minValue = values(4),
            maxValue = values(5),
            default = values(6),
            minSize = values(7),
            maxSize = values(8),
            regEx = values(9)
        )
    }
}
