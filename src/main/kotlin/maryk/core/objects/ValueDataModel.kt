package maryk.core.objects

import maryk.core.bytes.Base64
import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.definitions.IsFixedBytesEncodable
import maryk.core.properties.types.ValueDataObject

abstract class ValueDataModel<DO: ValueDataObject>(
        definitions: List<Def<*, DO>>
) : DataModel<DO>(definitions) {
    val byteSize: Int by lazy {
        var size = this.definitions.size - 1
        this.definitions.forEach {
            val def = it.propertyDefinition as IsFixedBytesEncodable<*>
            size += def.byteSize
        }
        size
    }

    /** Converts bytes to DataObject
     * @param bytes  to convertFromBytes
     * @param offset to start at
     * @return converted DataObject
     * @throws DefNotFoundException if definition needed for conversion is not found
     */
    @Throws(DefNotFoundException::class)
    fun createFromBytes(bytes: ByteArray, offset: Int = 0): DO {
        var byteOffset = offset
        val values = mutableMapOf<Int, Any>()
        this.definitions.forEach {
            val def = it.propertyDefinition as IsFixedBytesEncodable<*>

            values.put(
                    key = def.index.toInt(),
                    value = def.convertFromBytes(bytes, byteOffset, def.byteSize)
            )
            byteOffset += def.byteSize + 1
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
            def.convertToBytes(inputs[index], bytes, offset = offset)
            offset += def.byteSize + 1
            if(offset <= bytes.size) {
                bytes[offset - 1] = 1 // separator byte
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
        return this.createFromBytes(b, 0)
    }
}
