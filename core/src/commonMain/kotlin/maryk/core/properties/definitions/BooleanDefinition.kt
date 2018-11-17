package maryk.core.properties.definitions

import maryk.core.extensions.bytes.initBoolean
import maryk.core.extensions.bytes.writeBytes
import maryk.core.models.SimpleObjectDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.protobuf.WireType
import maryk.json.IsJsonLikeWriter
import maryk.lib.exceptions.ParseException

/** Definition for Boolean properties */
data class BooleanDefinition(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val default: Boolean? = null
):
    IsSimpleValueDefinition<Boolean, IsPropertyContext>,
    IsSerializableFixedBytesEncodable<Boolean, IsPropertyContext>,
    IsTransportablePropertyDefinitionType<Boolean>,
    HasDefaultValueDefinition<Boolean>
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

    override fun fromNativeType(value: Any) = value as? Boolean

    override fun writeJsonValue(value: Boolean, writer: IsJsonLikeWriter, context: IsPropertyContext?) {
        writer.writeBoolean(value)
    }

    object Model : SimpleObjectDataModel<BooleanDefinition, ObjectPropertyDefinitions<BooleanDefinition>>(
        properties = object : ObjectPropertyDefinitions<BooleanDefinition>() {
            init {
                IsPropertyDefinition.addIndexed(this, BooleanDefinition::indexed)
                IsPropertyDefinition.addRequired(this, BooleanDefinition::required)
                IsPropertyDefinition.addFinal(this, BooleanDefinition::final)
                add(4, "default", BooleanDefinition(), BooleanDefinition::default)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<BooleanDefinition>) = BooleanDefinition(
            indexed = map(1),
            required = map(2),
            final = map(3),
            default = map(4)
        )
    }
}
