package maryk.core.models.serializers

import maryk.core.base64.Base64Maryk
import maryk.core.models.IsValueDataModel
import maryk.core.models.invoke
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues

/**
 * Serializer for [ValueDataObject]s
 */
open class ValueDataModelSerializer<DO: ValueDataObject, DM: IsValueDataModel<DO, *>>(
    model: DM,
): ObjectDataModelSerializer<DO, DM, IsPropertyContext, IsPropertyContext>(model), IsValueDataModelSerializer<DO, DM> {
    override val byteSize by lazy {
        var size = -1
        for (it in model) {
            val def = it.definition as IsFixedStorageBytesEncodable<*>
            size += def.byteSize + 1
        }
        size
    }

    override fun readFromBytes(reader: () -> Byte): DO {
        val values = MutableValueItems()
        model.forEachIndexed { index, it ->
            if (index != 0) reader() // skip separation byte

            val def = it as IsFixedStorageBytesEncodable<*>
            values[it.index] = def.readStorageBytes(def.byteSize, reader)
        }
        return model.invoke(ObjectValues(model, values))
    }

    override fun toBytes(vararg inputs: Any): ByteArray {
        val bytes = ByteArray(byteSize)
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

    override fun fromBase64(value: String): DO {
        val b = Base64Maryk.decode(value)
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
