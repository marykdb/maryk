package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType
import maryk.core.values.SimpleObjectValues
import kotlin.random.Random

/** Definition for a bytes array with fixed length */
data class FixedBytesDefinition(
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
                IsPropertyDefinition.addRequired(this, FixedBytesDefinition::required)
                IsPropertyDefinition.addFinal(this, FixedBytesDefinition::final)
                IsComparableDefinition.addUnique(this, FixedBytesDefinition::unique)
                add(4, "minValue", FlexBytesDefinition(), FixedBytesDefinition::minValue)
                add(5, "maxValue", FlexBytesDefinition(), FixedBytesDefinition::maxValue)
                add(6, "default", FlexBytesDefinition(), FixedBytesDefinition::default)
                IsNumericDefinition.addRandom(7, this, FixedBytesDefinition::random)
                IsFixedBytesEncodable.addByteSize(8, this, FixedBytesDefinition::byteSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<FixedBytesDefinition>) = FixedBytesDefinition(
            required = values(1),
            final = values(2),
            unique = values(3),
            minValue = values(4),
            maxValue = values(5),
            default = values(6),
            random = values(7),
            byteSize = values(8)
        )
    }
}
