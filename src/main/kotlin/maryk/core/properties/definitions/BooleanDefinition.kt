package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initBoolean
import maryk.core.extensions.bytes.writeBytes
import maryk.core.json.IsJsonLikeWriter
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WireType

/** Definition for Boolean properties */
data class BooleanDefinition(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false
):
    IsSimpleValueDefinition<Boolean, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Boolean, IsPropertyContext>,
    IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.Boolean
    override val wireType = WireType.VAR_INT
    override val byteSize = 1

    override fun readStorageBytes(length: Int, reader: () -> Byte) = initBoolean(reader)

    override fun calculateStorageByteLength(value: Boolean) = this.byteSize

    override fun writeStorageBytes(value: Boolean, writer: (byte: Byte) -> Unit) = value.writeBytes(writer)

    override fun calculateTransportByteLength(value: Boolean) = this.byteSize

    override fun fromString(string: String) = when(string) {
        "true" -> true
        "false" -> false
        else -> throw ParseException(string)
    }

    override fun writeJsonValue(value: Boolean, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
        writer.writeValue(
            this.asString(value)
        )
    }

    internal object Model : SimpleDataModel<BooleanDefinition, PropertyDefinitions<BooleanDefinition>>(
        properties = object : PropertyDefinitions<BooleanDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, BooleanDefinition::indexed)
                IsPropertyDefinition.addSearchable(this, BooleanDefinition::searchable)
                IsPropertyDefinition.addRequired(this, BooleanDefinition::required)
                IsPropertyDefinition.addFinal(this, BooleanDefinition::final)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = BooleanDefinition(
            indexed = map[0] as Boolean,
            searchable = map[1] as Boolean,
            required = map[2] as Boolean,
            final = map[3] as Boolean
        )
    }
}