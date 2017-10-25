package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.computeVarByteSize
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ByteSizeContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Definition for objects with multiple types
 * @param typeMap definition of all sub types
 */
class MultiTypeDefinition(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val typeMap: Map<Int, AbstractSubDefinition<*>>
) : AbstractSubDefinition<TypedValue<*>>(
        name, index, indexed, searchable, required, final
) {
    @Throws(PropertyValidationException::class)
    override fun validate(previousValue: TypedValue<*>?, newValue: TypedValue<*>?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.typeMap[newValue.typeIndex] as AbstractSubDefinition<Any>?
                    ?: throw DefNotFoundException("No def found for index ${newValue.typeIndex} for ${this.getRef(parentRefFactory).completeName}")

            definition.validate(
                    previousValue?.value,
                    newValue.value
            ) {
                getRef(parentRefFactory)
            }
        }
    }

    override fun writeJsonValue(writer: JsonWriter, value: TypedValue<Any>) {
        writer.writeStartArray()
        writer.writeValue(value.typeIndex.toString())
        @Suppress("UNCHECKED_CAST")
        val definition = this.typeMap[value.typeIndex] as AbstractSubDefinition<Any>?
                ?: throw DefNotFoundException("No def found for index ${value.typeIndex} for $name")

        definition.writeJsonValue(writer, value.value)
        writer.writeEndArray()
    }

    override fun readJson(reader: JsonReader): TypedValue<*> {
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

        val definition: AbstractSubDefinition<*>? = this.typeMap[index]
                ?: throw ParseException("Unknown multitype index ${reader.lastValue} for $name")

        return TypedValue<Any>(
                index,
                definition!!.readJson(reader)
        )
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte): TypedValue<*> {
        // First the type value
        ProtoBuf.readKey(reader)
        val typeIndex = initIntByVar(reader)

        // Second the data itself
        val key = ProtoBuf.readKey(reader)
        val def = this.typeMap[typeIndex] ?: throw ParseException("Unknown multitype index $typeIndex for $name")

        val value = def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, reader),
                reader
        )

        return TypedValue(
                typeIndex,
                value
        )
    }

    override fun reserveTransportBytesWithKey(index: Int, value: TypedValue<*>, lengthCacher: (size: ByteSizeContainer) -> Unit): Int {
        // Cache length for length delimiter
        val container = ByteSizeContainer()
        lengthCacher(container)

        var totalByteSize = 0
        // Type index
        totalByteSize += ProtoBuf.reserveKey(1)
        totalByteSize += value.typeIndex.computeVarByteSize()

        // value
        @Suppress("UNCHECKED_CAST")
        val def = this.typeMap[value.typeIndex]!! as AbstractSubDefinition<Any>
        totalByteSize += def.reserveTransportBytesWithKey(2, value.value, lengthCacher)

        container.size = totalByteSize

        totalByteSize += ProtoBuf.reserveKey(this.index) // Add key length for field
        totalByteSize += container.size.computeVarByteSize() // Add field length for length delimiter
        return totalByteSize
    }

    override fun writeTransportBytesWithKey(index: Int, value: TypedValue<*>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(index, WireType.LENGTH_DELIMITED, writer)
        lengthCacheGetter().writeVarBytes(writer)
        this.writeTransportBytes(value, lengthCacheGetter, writer)
    }

    fun writeTransportBytes(value: TypedValue<*>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        value.typeIndex.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.typeMap[value.typeIndex]!! as AbstractSubDefinition<Any>
        def.writeTransportBytesWithKey(2, value.value, lengthCacheGetter, writer)
    }
}