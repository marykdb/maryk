package maryk.core.properties.definitions

import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WireType
import maryk.lib.bytes.calculateUTF8ByteLength
import maryk.lib.bytes.initString
import maryk.lib.bytes.writeUTF8Bytes

/** Definition for String properties */
data class StringDefinition(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
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
    IsWithDefaultDefinition<String>
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

    override fun validateWithRef(previousValue: String?, newValue: String?, refGetter: () -> IsPropertyReference<String, IsPropertyDefinition<String>>?) {
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

    object Model : SimpleDataModel<StringDefinition, PropertyDefinitions<StringDefinition>>(
        properties = object : PropertyDefinitions<StringDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, StringDefinition::indexed)
                IsPropertyDefinition.addSearchable(this, StringDefinition::searchable)
                IsPropertyDefinition.addRequired(this, StringDefinition::required)
                IsPropertyDefinition.addFinal(this, StringDefinition::final)
                IsComparableDefinition.addUnique(this, StringDefinition::unique)
                add(5, "minValue", StringDefinition(), StringDefinition::minValue)
                add(6, "maxValue", StringDefinition(), StringDefinition::maxValue)
                add(7, "default", StringDefinition(), StringDefinition::default)
                HasSizeDefinition.addMinSize(8, this) { it.minSize?.toUInt32() }
                HasSizeDefinition.addMaxSize(9, this) { it.maxSize?.toUInt32() }
                add(10, "regEx", StringDefinition(), StringDefinition::regEx)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = StringDefinition(
            indexed = map(0, false),
            searchable = map(1, true),
            required = map(2, true),
            final = map(3, false),
            unique = map(4, false),
            minValue = map(5),
            maxValue = map(6),
            default = map(7),
            minSize = map<UInt32?>(8)?.toInt(),
            maxSize = map<UInt32?>(9)?.toInt(),
            regEx = map(10)
        )
    }
}
