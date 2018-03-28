package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.IndexedEnum
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Definition for objects which can be of multiple defined types.
 * The type mapping is defined in the given [definitionMap] mapped by enum [E].
 * Receives context of [CX]
 */
data class MultiTypeDefinition<E: IndexedEnum<E>, in CX: IsPropertyContext>(
    override val indexed: Boolean = false,
    override val searchable: Boolean = true,
    override val required: Boolean = true,
    override val final: Boolean = false,
    val definitionMap: Map<E, IsSubDefinition<out Any, CX>>
) :
    IsValueDefinition<TypedValue<E, Any>, CX>,
    IsSerializableFlexBytesEncodable<TypedValue<E, Any>, CX>,
    IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.MultiType
    override val wireType = WireType.LENGTH_DELIMITED

    private val typeByName = definitionMap.map { Pair(it.key.name, it.key) }.toMap()
    private val typeByIndex = definitionMap.map { Pair(it.key.index, it.key) }.toMap()

    override fun asString(value: TypedValue<E, Any>, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: CX?): TypedValue<E, Any> {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun validateWithRef(previousValue: TypedValue<E, Any>?, newValue: TypedValue<E, Any>?, refGetter: () -> IsPropertyReference<TypedValue<E, Any>, IsPropertyDefinition<TypedValue<E, Any>>>?) {
        super<IsSerializableFlexBytesEncodable>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            @Suppress("UNCHECKED_CAST")
            val definition = this.definitionMap[newValue.type] as IsSubDefinition<Any, CX>?
                    ?: throw DefNotFoundException("No def found for index ${newValue.type}")

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

    override fun writeJsonValue(value: TypedValue<E, Any>, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeStartArray()
        writer.writeString(value.type.name)
        @Suppress("UNCHECKED_CAST")
        val definition = this.definitionMap[value.type] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("No def found for index ${value.type.name}")

        definition.writeJsonValue(value.value, writer, context)
        writer.writeEndArray()
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): TypedValue<E, Any> {
        reader.nextToken().let {
            if(it !is JsonToken.Value<*>) {
                throw ParseException("Expected a value at start")
            }

            val type = this.typeByName[it.value] ?: throw ParseException("Invalid multi type name ${it.value}")

            reader.nextToken()
            val definition = this.definitionMap[type]
                    ?: throw DefNotFoundException("Unknown multi type index ${type.index}")

            val value = definition.readJson(reader, context)

            reader.nextToken() // skip end object

            return TypedValue(type, value)
        }
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): TypedValue<E, Any> {
        // First the type value
        ProtoBuf.readKey(reader)
        val typeIndex = initIntByVar(reader)

        val type = this.typeByIndex[typeIndex] ?: throw ParseException("Unknown multi type index $typeIndex")

        // Second the data itself
        val key = ProtoBuf.readKey(reader)
        val def = this.definitionMap[type] ?: throw ParseException("Unknown multi type  $typeIndex")

        val value = def.readTransportBytes(
            ProtoBuf.getLength(key.wireType, reader),
            reader,
            context
        )

        return TypedValue(type, value)
    }

    override fun calculateTransportByteLength(value: TypedValue<E, Any>, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        // Type index
        totalByteLength += ProtoBuf.calculateKeyLength(1)
        totalByteLength += value.type.index.calculateVarByteLength()

        // value
        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMap[value.type] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        totalByteLength += def.calculateTransportByteLengthWithKey(2, value.value, cacher, context)

        return totalByteLength
    }

    override fun writeTransportBytes(value: TypedValue<E, Any>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        value.type.index.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMap[value.type] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        def.writeTransportBytesWithKey(2, value.value, cacheGetter, writer, context)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiTypeDefinition<*, *>) return false

        if (indexed != other.indexed) return false
        if (searchable != other.searchable) return false
        if (required != other.required) return false
        if (final != other.final) return false
        if (definitionMap != other.definitionMap) {
            if(definitionMap.size != other.definitionMap.size) return false
            definitionMap.entries.zip(other.definitionMap.entries).map {
                if(it.first.key.index != it.second.key.index
                    || it.first.key.name != it.second.key.name
                    || it.first.value != it.second.value) {
                    return false
                }
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = indexed.hashCode()
        result = 31 * result + searchable.hashCode()
        result = 31 * result + required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + definitionMap.hashCode()
        return result
    }

    internal object Model : SimpleDataModel<MultiTypeDefinition<*, *>, PropertyDefinitions<MultiTypeDefinition<*, *>>>(
        properties = object : PropertyDefinitions<MultiTypeDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, MultiTypeDefinition<*, *>::indexed)
                IsPropertyDefinition.addSearchable(this, MultiTypeDefinition<*, *>::searchable)
                IsPropertyDefinition.addRequired(this, MultiTypeDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, MultiTypeDefinition<*, *>::final)
                add(4, "definitionMap", ListDefinition(
                    valueDefinition =  SubModelDefinition(
                        dataModel = { MultiTypeDescriptor.Model }
                    )
                )) {
                    it.definitionMap.map {
                        MultiTypeDescriptor(
                            index = it.key.index.toUInt32(),
                            name = it.key.name,
                            definition = it.value
                        )
                    }.toList()
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
            definitionMap = (map[4] as List<MultiTypeDescriptor<IsPropertyContext>>).map {
                Pair(
                    IndexedEnum(it.index.toInt(), it.name),
                    it.definition
                )
            }.toMap() as Map<IndexedEnum<Any>, IsSubDefinition<out Any, IsPropertyContext>>
        )
    }
}

private data class MultiTypeDescriptor<in CX: IsPropertyContext>(
    val index: UInt32,
    val name: String,
    val definition: IsSubDefinition<out Any, CX>
) {
    internal object Model : SimpleDataModel<MultiTypeDescriptor<*>, PropertyDefinitions<MultiTypeDescriptor<*>>>(
        properties = object : PropertyDefinitions<MultiTypeDescriptor<*>>() {
            init {
                add(0, "index", NumberDefinition(type = UInt32)) { it.index }
                add(1, "name", StringDefinition()) { it.name }
                add(2, "definition", MultiTypeDefinition(
                    definitionMap = mapOfPropertyDefSubModelDefinitions
                )) {
                    val defType = it.definition as IsTransportablePropertyDefinitionType
                    TypedValue(defType.propertyDefinitionType, defType)
                }
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = MultiTypeDescriptor(
            index = map[0] as UInt32,
            name = map[1] as String,
            definition = (map[2] as TypedValue<IndexedEnum<Any>, IsSubDefinition<out Any, IsPropertyContext>>).value
        )
    }
}