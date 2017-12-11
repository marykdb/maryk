package maryk.core.properties.definitions

import maryk.core.extensions.randomBytes
import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.numeric.UInt32
import maryk.core.protobuf.WireType

/** Definition for a bytes array with fixed length */
data class FixedBytesDefinition(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val unique: Boolean = false,
        override val minValue: Bytes? = null,
        override val maxValue: Bytes? = null,
        override val random: Boolean = false,
        override val byteSize: Int
): IsNumericDefinition<Bytes>, IsSerializableFixedBytesEncodable<Bytes, IsPropertyContext> {
    override val wireType = WireType.LENGTH_DELIMITED

    override fun createRandom() = Bytes(randomBytes(this.byteSize))

    override fun readStorageBytes(length: Int, reader: () -> Byte) = Bytes.fromByteReader(byteSize, reader)

    override fun calculateStorageByteLength(value: Bytes) = this.byteSize

    override fun writeStorageBytes(value: Bytes, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Bytes) = this.byteSize

    override fun fromString(string: String) = Bytes.ofBase64String(string)

    companion object : DataModel<FixedBytesDefinition, PropertyDefinitions<FixedBytesDefinition>>(
            properties = object : PropertyDefinitions<FixedBytesDefinition>() {
                init {
                    IsPropertyDefinition.addIndexed(this, FixedBytesDefinition::indexed)
                    IsPropertyDefinition.addSearchable(this, FixedBytesDefinition::searchable)
                    IsPropertyDefinition.addRequired(this, FixedBytesDefinition::required)
                    IsPropertyDefinition.addFinal(this, FixedBytesDefinition::final)
                    IsComparableDefinition.addUnique(this, FixedBytesDefinition::unique)
                    add(5, "minValue", FlexBytesDefinition(), FixedBytesDefinition::minValue)
                    add(6, "maxValue", FlexBytesDefinition(), FixedBytesDefinition::maxValue)
                    IsNumericDefinition.addRandom(7, this, FixedBytesDefinition::random)
                    IsFixedBytesEncodable.addByteSize(8, this, FixedBytesDefinition::byteSize)
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = FixedBytesDefinition(
                indexed = map[0] as Boolean,
                searchable = map[1] as Boolean,
                required = map[2] as Boolean,
                final = map[3] as Boolean,
                unique = map[4] as Boolean,
                minValue = map[5] as Bytes?,
                maxValue = map[6] as Bytes?,
                random = map[7] as Boolean,
                byteSize = (map[8] as UInt32).toInt()
        )
    }
}