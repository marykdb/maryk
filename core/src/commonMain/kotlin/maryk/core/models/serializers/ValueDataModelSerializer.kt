package maryk.core.models.serializers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsValueModel
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.invoke
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.properties.values
import maryk.core.values.MutableValueItems
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Serializer for [ValueDataObject]s
 */
open class ValueDataModelSerializer<DO: ValueDataObject, DM: IsValueModel<DO, *>>(
    model: DM,
): ObjectDataModelSerializer<DO, DM, IsPropertyContext, IsPropertyContext>(model), IsValueDataModelSerializer<DO, DM> {
    override fun readFromBytes(reader: () -> Byte): DO {
        val values = MutableValueItems()
        model.forEachIndexed { index, it ->
            if (index != 0) reader() // skip separation byte

            val def = it as IsFixedStorageBytesEncodable<*>
            values[it.index] = def.readStorageBytes(def.byteSize, reader)
        }
        return model.invoke(model.values { values })
    }

    override fun toBytes(vararg inputs: Any): ByteArray {
        val bytes = ByteArray(model.Model.byteSize)
        var offset = 0

        model.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedStorageBytesEncodable<in Any>
            def.writeStorageBytes(inputs[index]) {
                bytes[offset++] = it
            }

            if (offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun fromBase64(value: String): DO {
        val b = Base64.Mime.decode(value)
        var index = 0
        return this.readFromBytes {
            b[index++]
        }
    }

    override fun getValueWithDefinition(
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: IsPropertyContext?
    ) = if (obj is ValueDataObjectWithValues) {
        obj.values(definition.index)
    } else {
        super.getValueWithDefinition(definition, obj, context)
    }
}
