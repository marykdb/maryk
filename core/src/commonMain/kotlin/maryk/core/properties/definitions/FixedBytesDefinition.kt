package maryk.core.properties.definitions

import maryk.core.models.SimpleObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionType.FixedBytes
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
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
) :
    IsNumericDefinition<Bytes>,
    IsSerializableFixedBytesEncodable<Bytes, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Bytes>,
    HasDefaultValueDefinition<Bytes>,
    IsWrappableDefinition<Bytes, IsPropertyContext, FixedBytesDefinitionWrapper<Bytes, Bytes, IsPropertyContext, FixedBytesDefinition, Any>> {
    override val propertyDefinitionType = FixedBytes
    override val wireType = LENGTH_DELIMITED

    override fun createRandom() = Bytes(Random.nextBytes(this.byteSize))

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun calculateStorageByteLength(value: Bytes) = this.byteSize

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = this.byteSize

    override fun fromString(string: String) = Bytes(string)

    override fun fromNativeType(value: Any) =
        if (value is ByteArray && value.size == this.byteSize) {
            Bytes(value)
        } else {
            value as? Bytes
        }

    override fun wrap(
        index: UInt,
        name: String,
        alternativeNames: Set<String>?
    ) =
        FixedBytesDefinitionWrapper<Bytes, Bytes, IsPropertyContext, FixedBytesDefinition, Any>(index, name, this, alternativeNames)


    object Model : SimpleObjectDataModel<FixedBytesDefinition, ObjectPropertyDefinitions<FixedBytesDefinition>>(
        properties = object : ObjectPropertyDefinitions<FixedBytesDefinition>() {
            init {
                IsPropertyDefinition.addRequired(this, FixedBytesDefinition::required)
                IsPropertyDefinition.addFinal(this, FixedBytesDefinition::final)
                IsComparableDefinition.addUnique(this, FixedBytesDefinition::unique)
                add(4u, "minValue", FlexBytesDefinition(), FixedBytesDefinition::minValue)
                add(5u, "maxValue", FlexBytesDefinition(), FixedBytesDefinition::maxValue)
                add(6u, "default", FlexBytesDefinition(), FixedBytesDefinition::default)
                IsNumericDefinition.addRandom(7u, this, FixedBytesDefinition::random)
                IsFixedStorageBytesEncodable.addByteSize(8u, this, FixedBytesDefinition::byteSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<FixedBytesDefinition>) = FixedBytesDefinition(
            required = values(1u),
            final = values(2u),
            unique = values(3u),
            minValue = values(4u),
            maxValue = values(5u),
            default = values(6u),
            random = values(7u),
            byteSize = values(8u)
        )
    }
}
