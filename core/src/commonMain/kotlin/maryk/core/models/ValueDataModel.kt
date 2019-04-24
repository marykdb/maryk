package maryk.core.models

import maryk.core.definitions.MarykPrimitive
import maryk.core.definitions.PrimitiveType.ValueModel
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.properties.IsDataModelPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.MutableObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.types.ValueDataObject
import maryk.core.properties.types.ValueDataObjectWithValues
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.lib.bytes.Base64

/**
 * ObjectDataModel of type [DO] for objects that can be encoded in fixed length width.
 * Contains [properties] definitions.
 */
abstract class ValueDataModel<DO : ValueDataObject, P : ObjectPropertyDefinitions<DO>>(
    name: String,
    properties: P
) : ObjectDataModel<DO, P>(name, properties), MarykPrimitive {
    override val primitiveType = ValueModel

    internal val byteSize: Int by lazy {
        var size = -1
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
        val values = MutableValueItems()
        this.properties.forEachIndexed { index, it ->
            if (index != 0) reader() // skip separation byte

            val def = it as IsFixedBytesEncodable<*>
            values[it.index] = def.readStorageBytes(def.byteSize, reader)
        }
        return this(this.values { values })
    }

    /** Creates bytes for given [values] */
    protected fun toBytes(values: ObjectValues<DO, P>): ByteArray {
        val bytes = ByteArray(this.byteSize)
        var offset = 0

        this.properties.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedBytesEncodable<in Any>
            def.writeStorageBytes(values(index.toUInt() + 1u)) {
                bytes[offset++] = it
            }

            if (offset < bytes.size) {
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
        val bytes = ByteArray(this.byteSize)
        var offset = 0

        this.properties.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedBytesEncodable<in Any>
            def.writeStorageBytes(inputs[index]) {
                bytes[offset++] = it
            }

            if (offset < bytes.size) {
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

    private object ValueDataModelProperties :
        ObjectPropertyDefinitions<ValueDataModel<*, *>>(),
        IsDataModelPropertyDefinitions<ValueDataModel<*, *>, ObjectPropertyDefinitionsCollectionDefinitionWrapper<ValueDataModel<*, *>>> {
        override val name = IsNamedDataModel.addName(this, ValueDataModel<*, *>::name)
        override val properties = addProperties(this)
    }

    internal object Model : DefinitionDataModel<ValueDataModel<*, *>>(
        properties = ValueDataModelProperties
    ) {
        override fun invoke(values: SimpleObjectValues<ValueDataModel<*, *>>) =
            object : ValueDataModel<ValueDataObjectWithValues, ObjectPropertyDefinitions<ValueDataObjectWithValues>>(
                name = values(1u),
                properties = values(2u)
            ) {
                override fun invoke(values: ObjectValues<ValueDataObjectWithValues, ObjectPropertyDefinitions<ValueDataObjectWithValues>>): ValueDataObjectWithValues {
                    return ValueDataObjectWithValues(toBytes(values), values)
                }
            }

        override fun writeJson(
            values: ObjectValues<ValueDataModel<*, *>, ObjectPropertyDefinitions<ValueDataModel<*, *>>>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            throw SerializationException("Cannot write definitions from values")
        }

        override fun writeJson(
            obj: ValueDataModel<*, *>,
            writer: IsJsonLikeWriter,
            context: ContainsDefinitionsContext?
        ) {
            this.writeDataModelJson(writer, context, obj, ValueDataModelProperties)
        }

        override fun walkJsonToRead(
            reader: IsJsonLikeReader,
            values: MutableValueItems,
            context: ContainsDefinitionsContext?
        ) {
            readDataModelJson(
                context, reader, values,
                properties = ValueDataModelProperties,
                propertyDefinitionsCreator = { MutableObjectPropertyDefinitions<ValueDataModel<*, *>>() }
            )
        }
    }
}
