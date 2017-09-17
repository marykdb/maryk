package maryk.core.objects

import maryk.core.bytes.Base64
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.types.ValueDataObject

abstract class ValueDataModel<DO: ValueDataObject>(
        constructor: (Map<Int, *>) -> DO,
        definitions: List<Def<*, DO>>
) : DataModel<DO>(constructor, definitions) {
    val byteSize: Int by lazy {
        var size = this.definitions.size - 1
        this.definitions.forEach {
            val def = it.propertyDefinition as IsFixedBytesEncodable<*>
            size += def.byteSize
        }
        size
    }

    /** Converts bytes from reader to DataObject
     * @param reader  to read from
     * @return converted DataObject
     * @throws DefNotFoundException if definition needed for conversion is not found
     */
    @Throws(DefNotFoundException::class)
    fun createFromBytes(reader: () -> Byte): DO {
        val values = mutableMapOf<Int, Any>()
        this.definitions.forEachIndexed { index, it ->
            if (index != 0) reader() // skip separation byte

            val def = it.propertyDefinition as IsFixedBytesEncodable<*>
            values.put(
                    key = def.index.toInt(),
                    value = def.convertFromBytes(def.byteSize, reader)
            )
        }
        return this.construct(values)
    }

    /** Constructs a new value with a map */
    abstract fun construct(values: Map<Int, Any>): DO

    /** Creates bytes for given inputs
     * @param inputs to convert to values
     */
    fun createBytes(vararg inputs: Any): ByteArray {
        val bytes =  ByteArray(this.byteSize)
        var offset = 0

        this.definitions.forEachIndexed { index, it ->
            @Suppress("UNCHECKED_CAST")
            val def = it.propertyDefinition as IsFixedBytesEncodable<in Any>
            def.convertToBytes(inputs[index], {}, {
                bytes[offset++] = it
            })

            if(offset < bytes.size) {
                bytes[offset++] = 1 // separator byte
            }
        }

        return bytes
    }

    /** Converts String to DataObject
     * @param value to convertFromBytes
     * @return converted DataObject
     * @throws DefNotFoundException if definition needed for conversion is not found
     */
    @Throws(DefNotFoundException::class)
    fun createFromString(value: String): DO {
        val b = Base64.decode(value)
        var index = 0
        return this.createFromBytes({
            b[index++]
        })
    }
}
