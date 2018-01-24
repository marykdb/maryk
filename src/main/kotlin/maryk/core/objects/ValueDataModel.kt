package maryk.core.objects

import maryk.core.bytes.Base64
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.ValueDataObject

/**
 * DataModel of type [DO] for objects that can be encoded in fixed length width.
 * Contains [properties] definitions.
 */
abstract class ValueDataModel<DO: ValueDataObject, out P: PropertyDefinitions<DO>>(
    name: String,
    properties: P
) : DataModel<DO, P>(name, properties) {
    val byteSize: Int by lazy {
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
        return this(values)
    }

    /** Creates bytes for given [inputs] */
    fun toBytes(vararg inputs: Any): ByteArray {
        val bytes =  ByteArray(this.byteSize)
        var offset = 0

        this.properties.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it as IsFixedBytesEncodable<in Any>
            def.writeStorageBytes(inputs[index], {
                bytes[offset++] = it
            })

            if(offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }

    /** Converts String [value] to DataObject
     * @throws DefNotFoundException if definition needed for conversion is not found
     */
    fun fromString(value: String): DO {
        val b = Base64.decode(value)
        var index = 0
        return this.readFromBytes({
            b[index++]
        })
    }

    internal object Model : DefinitionDataModel<ValueDataModel<*, *>>(
        properties = object : PropertyDefinitions<ValueDataModel<*, *>>() {
            init {
                AbstractDataModel.addName(this) {
                    it.name
                }
                AbstractDataModel.addProperties(this)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = object : ValueDataModel<ValueDataObject, PropertyDefinitions<ValueDataObject>>(
            name = map[0] as String,
            properties = map[1] as PropertyDefinitions<ValueDataObject>
        ){
            override fun invoke(map: Map<Int, *>): ValueDataObject {
                return object : ValueDataObject(ByteArray(0)){}
            }
        }
    }
}
