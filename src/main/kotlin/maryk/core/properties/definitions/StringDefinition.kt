package maryk.core.properties.definitions

import maryk.core.bytes.calculateUTF8ByteLength
import maryk.core.bytes.initString
import maryk.core.bytes.writeUTF8Bytes
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.InvalidSizeException
import maryk.core.properties.exceptions.InvalidValueException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.WireType

/** Definition for String properties */
data class StringDefinition(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: String? = null,
    override val maxValue: String? = null,
    override val minSize: Int? = null,
    override val maxSize: Int? = null,
    val regEx: String? = null
) :
    IsComparableDefinition<String, IsPropertyContext>,
    HasSizeDefinition,
    IsSerializableFlexBytesEncodable<String, IsPropertyContext>,
    IsTransportablePropertyDefinitionType
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

    internal object Model : SimpleDataModel<StringDefinition, PropertyDefinitions<StringDefinition>>(
        properties = object : PropertyDefinitions<StringDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, StringDefinition::indexed)
                IsPropertyDefinition.addSearchable(this, StringDefinition::searchable)
                IsPropertyDefinition.addRequired(this, StringDefinition::required)
                IsPropertyDefinition.addFinal(this, StringDefinition::final)
                IsComparableDefinition.addUnique(this, StringDefinition::unique)
                add(5, "minValue", StringDefinition(), StringDefinition::minValue)
                add(6, "maxValue", StringDefinition(), StringDefinition::maxValue)
                HasSizeDefinition.addMinSize(7, this) { it.minSize?.toUInt32() }
                HasSizeDefinition.addMaxSize(8, this) { it.maxSize?.toUInt32() }
                add(9, "regEx", StringDefinition(), StringDefinition::regEx)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = StringDefinition(
            indexed = map[0] as Boolean,
            searchable = map[1] as Boolean,
            required = map[2] as Boolean,
            final = map[3] as Boolean,
            unique = map[4] as Boolean,
            minValue = map[5] as String?,
            maxValue = map[6] as String?,
            minSize = (map[7] as UInt32?)?.toInt(),
            maxSize = (map[8] as UInt32?)?.toInt(),
            regEx = map[9] as String?
        )
    }
}