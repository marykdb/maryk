package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType
import maryk.lib.extensions.randomBytes

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

    override fun createRandom() = Bytes(randomBytes(this.byteSize))

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
                add(4, "minValue", FlexBytesDefinition(), FixedBytesDefinition::minValue)
                add(5, "maxValue", FlexBytesDefinition(), FixedBytesDefinition::maxValue)
                add(6, "default", FlexBytesDefinition(), FixedBytesDefinition::default)
                IsNumericDefinition.addRandom(7, this, FixedBytesDefinition::random)
                IsFixedBytesEncodable.addByteSize(8, this, FixedBytesDefinition::byteSize)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<FixedBytesDefinition>) = FixedBytesDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            unique = map(3),
            minValue = map(4),
            maxValue = map(5),
            default = map(6),
            random = map(7),
            byteSize = map(8)
        )
    }
}
