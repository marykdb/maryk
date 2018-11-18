package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType
import kotlin.random.Random

/** Definition for a bytes array with fixed length */
data class FixedBytesDefinition(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val unique: Boolean = false,
    override val minValue: Bytes? = null,
    override val maxValue: Bytes? = null,
    override val default: Bytes? = null,
    override val random: Boolean = false,
    override val byteSize: Int
):
    IsNumericDefinition<Bytes>,
    IsSerializableFixedBytesEncodable<Bytes, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Bytes>,
    HasDefaultValueDefinition<Bytes>
{
    override val propertyDefinitionType = PropertyDefinitionType.FixedBytes
    override val wireType = WireType.LENGTH_DELIMITED

    override fun createRandom() = Bytes(Random.nextBytes(this.byteSize))

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun calculateStorageByteLength(value: Bytes) = this.byteSize

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = this.byteSize

    override fun fromString(string: String) = Bytes(string)

    override fun fromNativeType(value: Any) =
        if(value is ByteArray && value.size == this.byteSize){
            Bytes(value)
        } else {
            value as? Bytes
        }

    object Model : SimpleObjectDataModel<FixedBytesDefinition, ObjectPropertyDefinitions<FixedBytesDefinition>>(
        properties = object : ObjectPropertyDefinitions<FixedBytesDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, FixedBytesDefinition::indexed)
                IsPropertyDefinition.addRequired(this, FixedBytesDefinition::required)
                IsPropertyDefinition.addFinal(this, FixedBytesDefinition::final)
                IsComparableDefinition.addUnique(this, FixedBytesDefinition::unique)
                add(5, "minValue", FlexBytesDefinition(), FixedBytesDefinition::minValue)
                add(6, "maxValue", FlexBytesDefinition(), FixedBytesDefinition::maxValue)
                add(7, "default", FlexBytesDefinition(), FixedBytesDefinition::default)
                IsNumericDefinition.addRandom(8, this, FixedBytesDefinition::random)
                IsFixedBytesEncodable.addByteSize(9, this, FixedBytesDefinition::byteSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<FixedBytesDefinition>) = FixedBytesDefinition(
            indexed = values(1),
            required = values(2),
            final = values(3),
            unique = values(4),
            minValue = values(5),
            maxValue = values(6),
            default = values(7),
            random = values(8),
            byteSize = values(9)
        )
    }
}
