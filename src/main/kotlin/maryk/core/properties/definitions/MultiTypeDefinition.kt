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
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Definition for objects with multiple types
 * @param getDefinition method to get definition
 */
class MultiTypeDefinition<in CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        val getDefinition: (Int) -> AbstractSubDefinition<*, CX>?
) : AbstractValueDefinition<TypedValue<*>, CX>(
        name, index, indexed, searchable, required, final, wireType = WireType.LENGTH_DELIMITED
) {
    override fun asString(value: TypedValue<*>, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, maryk.core.json.JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: CX?): TypedValue<*> {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    @Throws(ValidationException::class)
    override fun validate(previousValue: TypedValue<*>?, newValue: TypedValue<*>?, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.getDefinition(newValue.typeIndex) as AbstractSubDefinition<Any, CX>?
                    ?: throw DefNotFoundException("No def found for index ${newValue.typeIndex} for ${this.getRef(parentRefFactory).completeName}")

            definition.validate(
                    previousValue?.value,
                    newValue.value
            ) {
                getRef(parentRefFactory)
            }
        }
    }

    override fun getEmbeddedByName(name: String): IsPropertyDefinition<*>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinition<out Any>? = null

    override fun writeJsonValue(value: TypedValue<*>, writer: JsonWriter, context: CX?) {
        writer.writeStartArray()
        writer.writeValue(value.typeIndex.toString())
        @Suppress("UNCHECKED_CAST")
        val definition = this.getDefinition(value.typeIndex) as AbstractSubDefinition<Any, CX>?
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

        val definition: AbstractSubDefinition<*, CX>? = this.getDefinition(index)
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
        val def = this.getDefinition(typeIndex) ?: throw ParseException("Unknown multitype index $typeIndex for $name")

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

    override fun calculateTransportByteLength(value: TypedValue<*>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?): Int {
        var totalByteLength = 0
        // Type index
        totalByteLength += ProtoBuf.calculateKeyLength(1)
        totalByteLength += value.typeIndex.calculateVarByteLength()

        // value
        @Suppress("UNCHECKED_CAST")
        val def = this.getDefinition(value.typeIndex)!! as AbstractSubDefinition<Any, CX>
        totalByteLength += def.calculateTransportByteLengthWithKey(2, value.value, lengthCacher, context)

        return totalByteLength
    }

    override fun writeTransportBytes(value: TypedValue<*>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        value.typeIndex.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.getDefinition(value.typeIndex)!! as AbstractSubDefinition<Any, CX>
        def.writeTransportBytesWithIndexKey(2, value.value, lengthCacheGetter, writer, context)
    }
}