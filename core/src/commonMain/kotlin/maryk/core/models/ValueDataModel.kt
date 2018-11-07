package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.ObjectValues
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.lib.bytes.Base64

/**
 * ObjectDataModel of type [DO] for objects that can be encoded in fixed length width.
 * Contains [properties] definitions.
 */
abstract class ValueDataModel<DO: ValueDataObject, P: ObjectPropertyDefinitions<DO>>(
    name: String,
    properties: P
) : ObjectDataModel<DO, P>(name, properties), MarykPrimitive {
    override val primitiveType = PrimitiveType.ValueModel

    internal val byteSize: Int by lazy {
        var size = - 1
        for (it in this.properties) {
            val def = it.definition as IsFixedBytesEncodable<*>
            size += def.byteSize + 1
        }
        size
    }

    /** Read bytes from [reader] to DataObject
     * @throws DefNotFoundException if definition needed for conversion is not found
     */
    fun readFromBytes(reader: () -> Byte): DO {
        val values = mutableMapOf<Int, Any>()
        this.properties.forEachIndexed { index, it ->
            if (index != 0) reader() // skip separation byte

            val def = it as IsFixedBytesEncodable<*>
            values[it.index] = def.readStorageBytes(def.byteSize, reader)
        }
        return this(this.map { values })
    }


    /** Creates bytes for given [values] */
    protected fun toBytes(values: ObjectValues<DO, P>): ByteArray {
        val bytes =  ByteArray(this.byteSize)
        var offset = 0

        this.properties.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedBytesEncodable<in Any>
            def.writeStorageBytes(values(index + 1)) {
                bytes[offset++] = it
            }

            if(offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }

    override fun getValueWithDefinition(
        definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        obj: DO,
        context: IsPropertyContext?
    ) = if (obj is ValueDataObjectWithValues) {
        obj.values(definition.index)
    } else {
        super.getValueWithDefinition(definition, obj, context)
    }

    /** Creates bytes for given [inputs] */
    protected fun toBytes(vararg inputs: Any): ByteArray {
        val bytes =  ByteArray(this.byteSize)
        var offset = 0

        this.properties.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedBytesEncodable<in Any>
            def.writeStorageBytes(inputs[index]) {
                bytes[offset++] = it
            }

            if(offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }

    /** Converts String [value] to DataObject
     * @throws DefNotFoundException if definition needed for conversion is not found
     */
    fun fromBase64(value: String): DO {
        val b = Base64.decode(value)
        var index = 0
        return this.readFromBytes {
            b[index++]
        }
    }

    internal object Model : DefinitionDataModel<ValueDataModel<*, *>>(
        properties = object : ObjectPropertyDefinitions<ValueDataModel<*, *>>() {
            init {
                IsNamedDataModel.addName(this) {
                    it.name
                }
                ObjectDataModel.addProperties(this)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ValueDataModel<*, *>>) = object : ValueDataModel<ValueDataObjectWithValues, ObjectPropertyDefinitions<ValueDataObjectWithValues>>(
            name = map(1),
            properties = map(2)
        ){
            override fun invoke(map: ObjectValues<ValueDataObjectWithValues, ObjectPropertyDefinitions<ValueDataObjectWithValues>>): ValueDataObjectWithValues {
                return ValueDataObjectWithValues(toBytes(map), map)
            }
        }
    }
}
