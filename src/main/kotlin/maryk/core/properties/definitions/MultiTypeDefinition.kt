package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Definition for objects with multiple types
 * @param typeMap definition of all sub types
 */
class MultiTypeDefinition<in CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val typeMap: Map<Int, AbstractSubDefinition<*, CX>>
) : AbstractSubDefinition<TypedValue<*>, CX>(
        name, index, indexed, searchable, required, final
) {
    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: TypedValue<*>?, newValue: TypedValue<*>?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.typeMap[newValue.typeIndex] as AbstractSubDefinition<Any, CX>?
                    ?: throw DefNotFoundException("No def found for index ${newValue.typeIndex} for ${this.getRef(parentRefFactory).completeName}")

            definition.validate(
                    previousValue?.value,
                    newValue.value
            ) {
                getRef(parentRefFactory)
            }
        }
    }

    override fun writeJsonValue(value: TypedValue<*>, writer: JsonWriter, context: CX?) {
        writer.writeStartArray()
        writer.writeValue(value.typeIndex.toString())
        @Suppress("UNCHECKED_CAST")
        val definition = this.typeMap[value.typeIndex] as AbstractSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("No def found for index ${value.typeIndex} for $name")

        definition.writeJsonValue(value.value, writer, context)
        writer.writeEndArray()
    }

    override fun readJson(reader: JsonReader, context: CX?): TypedValue<*> {
        if(reader.nextToken() !is JsonToken.ARRAY_VALUE) {
            throw ParseException("Expected an array value at start")
        }

        val index: Int
        try {
            index = reader.lastValue.toInt()
        }catch (e: Throwable) {
            throw ParseException("Invalid multitype index ${reader.lastValue} for $name")
        }
        reader.nextToken()

        val definition: AbstractSubDefinition<*, CX>? = this.typeMap[index]
                ?: throw ParseException("Unknown multitype index ${reader.lastValue} for $name")

        val value = definition!!.readJson(reader, context)

        reader.nextToken() // skip end object

        return TypedValue(index, value)
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): TypedValue<*> {
        // First the type value
        ProtoBuf.readKey(reader)
        val typeIndex = initIntByVar(reader)

        // Second the data itself
        val key = ProtoBuf.readKey(reader)
        val def = this.typeMap[typeIndex] ?: throw ParseException("Unknown multitype index $typeIndex for $name")

        val value = def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, reader),
                reader,
                context
        )

        return TypedValue(
                typeIndex,
                value
        )
    }

    override fun calculateTransportByteLengthWithKey(index: Int, value: TypedValue<*>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?): Int {
        // Cache length for length delimiter
        val container = ByteLengthContainer()
        lengthCacher(container)

        var totalByteLength = 0
        // Type index
        totalByteLength += ProtoBuf.calculateKeyLength(1)
        totalByteLength += value.typeIndex.calculateVarByteLength()

        // value
        @Suppress("UNCHECKED_CAST")
        val def = this.typeMap[value.typeIndex]!! as AbstractSubDefinition<Any, CX>
        totalByteLength += def.calculateTransportByteLengthWithKey(2, value.value, lengthCacher, context)

        container.length = totalByteLength

        totalByteLength += ProtoBuf.calculateKeyLength(this.index) // Add key length for field
        totalByteLength += container.length.calculateVarByteLength() // Add field length for length delimiter
        return totalByteLength
    }

    override fun writeTransportBytesWithKey(index: Int, value: TypedValue<*>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(index, WireType.LENGTH_DELIMITED, writer)
        lengthCacheGetter().writeVarBytes(writer)
        this.writeTransportBytes(context, value, lengthCacheGetter, writer)
    }

    /** Write transport bytes for MultiType
     * Will be an object with a type index on key=1 and the value on key=2
     * @param value to write
     * @param lengthCacheGetter to fetch cached length of value if needed
     * @param writer to write the bytes with
     */
    fun writeTransportBytes(context: CX?, value: TypedValue<*>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        value.typeIndex.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.typeMap[value.typeIndex]!! as AbstractSubDefinition<Any, CX>
        def.writeTransportBytesWithKey(2, value.value, lengthCacheGetter, writer, context)
    }
}