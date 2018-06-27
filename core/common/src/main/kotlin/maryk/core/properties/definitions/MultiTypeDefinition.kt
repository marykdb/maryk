package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.descriptors.addDescriptorPropertyWrapperWrapper
import maryk.core.properties.definitions.descriptors.convertMultiTypeDescriptors
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonToken
import maryk.json.JsonWriter
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/**
 * Definition for objects which can be of multiple defined types.
 * The type mapping is defined in the given [definitionMap] mapped by enum [E].
 * Receives context of [CX]
 */
data class MultiTypeDefinition<E: IndexedEnum<E>, in CX: IsPropertyContext>(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    val typeEnum: IndexedEnumDefinition<E>,
    val definitionMap: Map<E, IsSubDefinition<out Any, CX>>,
    override val default: TypedValue<E, *>? = null
) :
    IsValueDefinition<TypedValue<E, Any>, CX>,
    IsSerializableFlexBytesEncodable<TypedValue<E, Any>, CX>,
    IsTransportablePropertyDefinitionType<TypedValue<E, Any>>,
    HasDefaultValueDefinition<TypedValue<E, Any>>
{
    override val propertyDefinitionType = PropertyDefinitionType.MultiType
    override val wireType = WireType.LENGTH_DELIMITED

    private val typeByName = definitionMap.map { Pair(it.key.name, it.key) }.toMap()
    private val typeByIndex = definitionMap.map { Pair(it.key.index, it.key) }.toMap()
    private val definitionMapByIndex = definitionMap.map { Pair(it.key.index, it.value) }.toMap()

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
            val definition = this.definitionMapByIndex[newValue.type.index] as IsSubDefinition<Any, CX>?
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

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun writeJsonValue(value: TypedValue<E, Any>, writer: IsJsonLikeWriter, context: CX?) {
        @Suppress("UNCHECKED_CAST")
        val definition = this.definitionMapByIndex[value.type.index] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("No def found for index ${value.type.name}")

        if (writer is YamlWriter) {
            writer.writeTag("!${value.type.name}")
            definition.writeJsonValue(value.value, writer, context)
        } else {
            writer.writeStartArray()
            writer.writeString(value.type.name)

            definition.writeJsonValue(value.value, writer, context)
            writer.writeEndArray()
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): TypedValue<E, Any> {
        if(reader is IsYamlReader) {
            val token = reader.currentToken as? TokenWithType
                    ?: throw ParseException("Expected an Token with YAML type tag which describes property type")

            val tokenType = token.type
            val type: E = when (tokenType) {
                is IndexedEnum<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    tokenType as E
                }
                is UnknownYamlTag -> {
                    this.typeByName[tokenType.name]
                            ?: throw DefNotFoundException("Unknown type ${tokenType.name}")
                }
                else -> throw ParseException("Unknown tag type for $tokenType")
            }

            val definition = this.definitionMapByIndex[type.index]
                    ?: throw DefNotFoundException("No definition for type $type")

            return TypedValue(type, definition.readJson(reader, context))
        } else {
            reader.nextToken().let {
                if (it !is JsonToken.Value<*>) {
                    throw ParseException("Expected a value at start")
                }

                val type = this.typeByName[it.value] ?: throw ParseException("Invalid multi type name ${it.value}")
                val definition = this.definitionMapByIndex[type.index]
                        ?: throw DefNotFoundException("Unknown multi type index ${type.index}")

                reader.nextToken()
                val value = definition.readJson(reader, context)
                reader.nextToken() // skip end object

                return TypedValue(type, value)
            }
        }
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): TypedValue<E, Any> {
        // First the type value
        ProtoBuf.readKey(reader)
        val typeIndex = initIntByVar(reader)

        val type = this.typeByIndex[typeIndex] ?: throw ParseException("Unknown multi type index $typeIndex")

        // Second the data itself
        val key = ProtoBuf.readKey(reader)
        val def = this.definitionMapByIndex[type.index] ?: throw ParseException("Unknown multi type  $typeIndex")

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
        val def = this.definitionMapByIndex[value.type.index] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        totalByteLength += def.calculateTransportByteLengthWithKey(2, value.value, cacher, context)

        return totalByteLength
    }

    override fun writeTransportBytes(value: TypedValue<E, Any>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        ProtoBuf.writeKey(1, WireType.VAR_INT, writer)
        value.type.index.writeVarBytes(writer)

        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMapByIndex[value.type.index] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        def.writeTransportBytesWithKey(2, value.value, cacheGetter, writer, context)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiTypeDefinition<*, *>) return false

        if (indexed != other.indexed) return false
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
        result = 31 * result + required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + definitionMap.hashCode()
        return result
    }

    object Model : ContextualDataModel<MultiTypeDefinition<*, *>, PropertyDefinitions<MultiTypeDefinition<*, *>>, DataModelContext, MultiTypeDefinitionContext>(
        contextTransformer = { MultiTypeDefinitionContext(it) },
        properties = object : PropertyDefinitions<MultiTypeDefinition<*, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, MultiTypeDefinition<*, *>::indexed)
                IsPropertyDefinition.addRequired(this, MultiTypeDefinition<*, *>::required)
                IsPropertyDefinition.addFinal(this, MultiTypeDefinition<*, *>::final)

                add(3, "typeEnum",
                    StringDefinition(),
                    getter = MultiTypeDefinition<*, *>::typeEnum,
                    capturer = { context: MultiTypeDefinitionContext, value ->
                        context.typeEnumName = value
                    },
                    toSerializable = { value, _ ->
                        value?.name
                    },
                    fromSerializable = { null }
                )

                this.addDescriptorPropertyWrapperWrapper(4, "definitionMap")

                @Suppress("UNCHECKED_CAST")
                add(5, "default",
                    ContextualValueDefinition(
                        required = false,
                        contextTransformer = { context: MultiTypeDefinitionContext? ->
                            context?.dataModelContext
                        },
                        contextualResolver = { context: MultiTypeDefinitionContext? ->
                            context?.multiTypeDefinition ?: throw ContextNotFoundException()
                        }
                    ) as IsSerializableFlexBytesEncodable<TypedValue<out IndexedEnum<*>, Any>, MultiTypeDefinitionContext>,
                    MultiTypeDefinition<*, *>::default
                )
            }
        }
    ) {
        override fun invoke(map: DataObjectMap<MultiTypeDefinition<*, *>>): MultiTypeDefinition<IndexedEnum<Any>, DataModelContext> {
            val definitionMap = convertMultiTypeDescriptors(
                map(4)
            )

            val typeOptions = definitionMap.keys.toTypedArray()

            val typeEnum = IndexedEnumDefinition(
                map(3)
            ) { typeOptions }

            return MultiTypeDefinition(
                indexed = map(0),
                required = map(1),
                final = map(2),
                typeEnum = typeEnum,
                definitionMap = definitionMap,
                default = map(5)
            )
        }
    }
}

class MultiTypeDefinitionContext(
    val dataModelContext: DataModelContext?
): IsPropertyContext {
    var typeEnumName: String? = null

    var definitionMap: Map<IndexedEnum<Any>, IsSubDefinition<out Any, DataModelContext>> ?= null

    private var _multiTypeDefinition: Lazy<MultiTypeDefinition<IndexedEnum<Any>, DataModelContext>> = lazy {
        val typeOptions = definitionMap?.keys?.toTypedArray() ?: throw ContextNotFoundException()

        val typeEnum = IndexedEnumDefinition(
            typeEnumName ?: throw ContextNotFoundException()
        ) { typeOptions }

        MultiTypeDefinition(
            typeEnum = typeEnum,
            definitionMap = this.definitionMap ?: throw ContextNotFoundException()
        )
    }

    val multiTypeDefinition: MultiTypeDefinition<IndexedEnum<Any>, DataModelContext> get() = this._multiTypeDefinition.value
}
