package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Definition for objects with multiple types
 * @param getDefinition method to get definition
 */
class MultiTypeDefinition<CX: IsPropertyContext>(
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = true,
        final: Boolean = false,
        val getDefinition: (Int) -> AbstractSubDefinition<*, CX>?
) : AbstractValueDefinition<TypedValue<*>, CX>(
        indexed, searchable, required, final, wireType = WireType.LENGTH_DELIMITED
), IsSerializableFlexBytesEncodable<TypedValue<*>, CX> {
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

    override fun validateWithRef(previousValue: TypedValue<*>?, newValue: TypedValue<*>?, refGetter: () -> IsPropertyReference<TypedValue<*>, IsPropertyDefinition<TypedValue<*>>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.getDefinition(newValue.typeIndex) as AbstractSubDefinition<Any, CX>?
                    ?: throw DefNotFoundException("No def found for index ${newValue.typeIndex}")

            definition.validateWithRef(
                    previousValue?.value,
                    newValue.value
            ) {
                @Suppress("UNCHECKED_CAST")
                refGetter() as IsPropertyReference<Any, IsPropertyDefinition<Any>>?
            }
        }
    }

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun writeJsonValue(value: TypedValue<*>, writer: JsonWriter, context: CX?) {
        writer.writeStartArray()
        writer.writeValue(value.typeIndex.toString())
        @Suppress("UNCHECKED_CAST")
        val definition = this.getDefinition(value.typeIndex) as AbstractSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("No def found for index ${value.typeIndex}")

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
            throw ParseException("Invalid multitype index ${reader.lastValue}")
        }
        reader.nextToken()

        val definition: AbstractSubDefinition<*, CX>? = this.getDefinition(index)
                ?: throw ParseException("Unknown multitype index ${reader.lastValue}")

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
        val def = this.getDefinition(typeIndex) ?: throw ParseException("Unknown multitype index $typeIndex")

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
        def.writeTransportBytesWithKey(2, value.value, lengthCacheGetter, writer, context)
    }
}