package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/** Definition for objects which can be of multiple defined types. The type mapping is defined in the given
 * [definitionMap].
 */
data class MultiTypeDefinition<in CX: IsPropertyContext>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        val definitionMap: Map<Int, IsSubDefinition<*, CX>>
) :
        IsValueDefinition<TypedValue<*>, CX>,
        IsSerializableFlexBytesEncodable<TypedValue<*>, CX>,
        IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.MultiType
    override val wireType = WireType.LENGTH_DELIMITED

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
        super<IsSerializableFlexBytesEncodable>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.definitionMap[newValue.typeIndex] as IsSubDefinition<Any, CX>?
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
        val definition = this.definitionMap[value.typeIndex] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("No def found for index ${value.typeIndex}")

        definition.writeJsonValue(value.value, writer, context)
        writer.writeEndArray()
    }

    override fun readJson(reader: JsonReader, context: CX?): TypedValue<*> {
        if(reader.nextToken() !is JsonToken.ArrayValue) {
            throw ParseException("Expected an array value at start")
        }

        val index: Int
        try {
            index = reader.lastValue.toInt()
        }catch (e: Throwable) {
            throw ParseException("Invalid multi type index ${reader.lastValue}")
        }
        reader.nextToken()

        val definition: IsSubDefinition<*, CX>? = this.definitionMap[index]
                ?: throw ParseException("Unknown multi type index ${reader.lastValue}")

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
        val def = this.definitionMap[typeIndex] ?: throw ParseException("Unknown multi type index $typeIndex")

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

    override fun calculateTransportByteLength(value: TypedValue<*>, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        // Type index
        totalByteLength += ProtoBuf.calculateKeyLength(1)
        totalByteLength += value.typeIndex.calculateVarByteLength()

        // value
        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMap[value.typeIndex]!! as IsSubDefinition<Any, CX>
        totalByteLength += def.calculateTransportByteLengthWithKey(2, value.value, cacher, context)

        return totalByteLength
    }

    override fun writeTransportBytes(value: TypedValue<*>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        value.typeIndex.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMap[value.typeIndex]!! as IsSubDefinition<Any, CX>
        def.writeTransportBytesWithKey(2, value.value, cacheGetter, writer, context)
    }

    object Model : SimpleDataModel<MultiTypeDefinition<*>, PropertyDefinitions<MultiTypeDefinition<*>>>(
            properties = object : PropertyDefinitions<MultiTypeDefinition<*>>() {
                init {
                    IsPropertyDefinition.addIndexed(this, MultiTypeDefinition<*>::indexed)
                    IsPropertyDefinition.addSearchable(this, MultiTypeDefinition<*>::searchable)
                    IsPropertyDefinition.addRequired(this, MultiTypeDefinition<*>::required)
                    IsPropertyDefinition.addFinal(this, MultiTypeDefinition<*>::final)
                    add(4, "definitionMap", MapDefinition(
                            keyDefinition = NumberDefinition(type = UInt32),
                            valueDefinition =  MultiTypeDefinition(
                                    definitionMap = mapOfPropertyDefSubModelDefinitions
                            )
                    )) {
                        it.definitionMap.map {
                            val defType = it.value as IsTransportablePropertyDefinitionType
                            it.key.toUInt32() to TypedValue(defType.propertyDefinitionType.index, it.value)
                        }.toMap()
                    }
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = MultiTypeDefinition(
                indexed = map[0] as Boolean,
                searchable = map[1] as Boolean,
                required = map[2] as Boolean,
                final = map[3] as Boolean,
                definitionMap = (map[4] as Map<UInt32, TypedValue<IsValueDefinition<*, *>>>).map {
                    it.key.toInt() to it.value.value
                }.toMap()
        )
    }
}