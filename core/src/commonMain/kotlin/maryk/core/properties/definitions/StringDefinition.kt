package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.WireType
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.bytes.initString
import maryk.lib.bytes.writeUTF8Bytes

/** Definition for String properties */
data class StringDefinition(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: String? = null,
    override val maxValue: String? = null,
    override val default: String? = null,
    override val minSize: Int? = null,
    override val maxSize: Int? = null,
    val regEx: String? = null
) :
    IsComparableDefinition<String, IsPropertyContext>,
    HasSizeDefinition,
    IsSerializableFlexBytesEncodable<String, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<String>,
    HasDefaultValueDefinition<String>
{
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

    override fun validateWithRef(previousValue: String?, newValue: String?, refGetter: () -> IsPropertyReference<String, IsPropertyDefinition<String>, *>?) {
        super<IsComparableDefinition>.validateWithRef(previousValue, newValue, refGetter)

        when {
            newValue != null -> {
                when {
                    isSizeToSmall(newValue.length) || isSizeToBig(newValue.length)
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
                IsPropertyDefinition.addIndexed(this, StringDefinition::indexed)
                IsPropertyDefinition.addRequired(this, StringDefinition::required)
                IsPropertyDefinition.addFinal(this, StringDefinition::final)
                IsComparableDefinition.addUnique(this, StringDefinition::unique)
                add(5, "minValue", StringDefinition(), StringDefinition::minValue)
                add(6, "maxValue", StringDefinition(), StringDefinition::maxValue)
                add(7, "default", StringDefinition(), StringDefinition::default)
                HasSizeDefinition.addMinSize(8, this, StringDefinition::minSize)
                HasSizeDefinition.addMaxSize(9, this, StringDefinition::maxSize)
                add(10, "regEx", StringDefinition(), StringDefinition::regEx)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<StringDefinition>) = StringDefinition(
            indexed = values(1),
            required = values(2),
            final = values(3),
            unique = values(4),
            minValue = values(5),
            maxValue = values(6),
            default = values(7),
            minSize = values(8),
            maxSize = values(9),
            regEx = values(10)
        )
    }
}
