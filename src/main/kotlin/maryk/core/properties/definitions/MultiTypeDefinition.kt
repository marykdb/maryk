package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.json.JsonToken
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.references.PropertyReference
import maryk.core.properties.types.TypedValue
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

    override fun writeJsonValue(generator: JsonGenerator, value: TypedValue<Any>) {
        generator.writeStartArray()
        generator.writeValue(value.typeIndex.toString())
        @Suppress("UNCHECKED_CAST")
        val definition = this.typeMap[value.typeIndex] as AbstractSubDefinition<Any>?
                ?: throw DefNotFoundException("No def found for index ${value.typeIndex} for $name")

        definition.writeJsonValue(generator, value.value)
        generator.writeEndArray()
    }

    override fun parseFromJson(parser: JsonParser): TypedValue<*> {
        if(parser.nextToken() !is JsonToken.ARRAY_VALUE) {
            throw ParseException("Expected an array value at start")
        }

        val index: Int
        try {
            index = parser.lastValue.toInt()
        }catch (e: Throwable) {
            throw ParseException("Invalid multitype index ${parser.lastValue} for $name")
        }
        parser.nextToken()

        val definition: AbstractSubDefinition<*>? = this.typeMap[index]
                ?: throw ParseException("Unknown multitype index ${parser.lastValue} for $name")

        return TypedValue<Any>(
                index,
                definition!!.parseFromJson(parser)
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

        val endKey = ProtoBuf.readKey(reader)
        if (endKey.wireType != WireType.END_GROUP) {
            throw ParseException("Invalid protobuf multiType construction")
        }

        return TypedValue(
                typeIndex,
                value
        )
    }

    override fun writeTransportBytesWithKey(index: Int, value: TypedValue<*>, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(this.index, WireType.START_GROUP, reserver, writer)
        this.writeTransportBytes(value, reserver, writer)
    }

    fun writeTransportBytes(value: TypedValue<*>, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, reserver, writer)
        value.typeIndex.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.typeMap[value.typeIndex]!! as AbstractSubDefinition<Any>
        def.writeTransportBytesWithKey(2, value.value, reserver, writer)
        ProtoBuf.writeKey(this.index, WireType.END_GROUP, reserver, writer)
    }
}