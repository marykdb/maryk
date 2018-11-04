package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.objects.SimpleObjectValues
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
        override fun invoke(map: SimpleObjectValues<FixedBytesDefinition>) = FixedBytesDefinition(
            indexed = map(1),
            required = map(2),
            final = map(3),
            unique = map(4),
            minValue = map(5),
            maxValue = map(6),
            default = map(7),
            random = map(8),
            byteSize = map(9)
        )
    }
}