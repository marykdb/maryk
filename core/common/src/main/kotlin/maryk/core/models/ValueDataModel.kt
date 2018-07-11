package maryk.core.models

import maryk.core.definitions.PrimitiveType
import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.ObjectValues
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.types.ValueDataObject
import maryk.lib.bytes.Base64

/**
 * ObjectDataModel of type [DO] for objects that can be encoded in fixed length width.
 * Contains [properties] definitions.
 */
abstract class ValueDataModel<DO: ValueDataObject, P: ObjectPropertyDefinitions<DO>>(
    name: String,
    properties: P
) : ObjectDataModel<DO, P>(name, properties) {
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
    internal fun readFromBytes(reader: () -> Byte): DO {
        val values = mutableMapOf<Int, Any>()
        this.properties.forEachIndexed { index, it ->
            if (index != 0) reader() // skip separation byte

            val def = it as IsFixedBytesEncodable<*>
            values[it.index] = def.readStorageBytes(def.byteSize, reader)
        }
        return this(this.map { values })
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
    internal fun fromString(value: String): DO {
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
        override fun invoke(map: SimpleObjectValues<ValueDataModel<*, *>>) = object : ValueDataModel<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>(
            name = map(0),
            properties = map(1)
        ){
            override fun invoke(map: ObjectValues<ValueDataObject, ObjectPropertyDefinitions<ValueDataObject>>): ValueDataObject {
                return object : ValueDataObject(ByteArray(0)){}
            }
        }
    }
}
