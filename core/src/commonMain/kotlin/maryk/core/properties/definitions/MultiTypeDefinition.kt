package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.exceptions.TypeException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.ContextualDataModel
import maryk.core.models.IsTypedValuesDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.descriptors.addDescriptorPropertyWrapperWrapper
import maryk.core.properties.definitions.descriptors.convertMultiTypeDescriptors
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MultiAnyTypeReference
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.values.EmptyValueItems
import maryk.core.values.SimpleObjectValues
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
data class MultiTypeDefinition<E : IndexedEnum<E>, in CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val typeEnum: IndexedEnumDefinition<E>,
    override val typeIsFinal: Boolean = true,
    override val definitionMap: Map<E, IsSubDefinition<out Any, CX>>,
    override val default: TypedValue<E, *>? = null,
    internal val keepAsValues: Boolean = false
) : IsMultiTypeDefinition<E, CX> {
    override val propertyDefinitionType = PropertyDefinitionType.MultiType
    override val wireType = WireType.LENGTH_DELIMITED

    private val definitionMapByIndex = definitionMap.map { Pair(it.key.index, it.value) }.toMap()

    init {
        if (this.final) {
            require(this.typeIsFinal) { "typeIsFinal should be true if multi type definition is final" }
        }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        typeEnum: IndexedEnumDefinition<E>,
        typeIsFinal: Boolean = true,
        definitionMap: Map<E, IsUsableInMultiType<out Any, CX>>,
        default: TypedValue<E, *>? = null
    ) : this(required, final, typeEnum, typeIsFinal, definitionMap as Map<E, IsSubDefinition<out Any, CX>>, default)

    override fun definition(index: UInt) = definitionMapByIndex[index]

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

    override fun validateWithRef(
        previousValue: TypedValue<E, Any>?,
        newValue: TypedValue<E, Any>?,
        refGetter: () -> IsPropertyReference<TypedValue<E, Any>, IsPropertyDefinition<TypedValue<E, Any>>, *>?
    ) {
        super.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            if (this.typeIsFinal && previousValue != null) {
                if (newValue.type != previousValue.type) {
                    throw AlreadySetException(
                        this.typeRef(
                            newValue.type,
                            refGetter() as CanHaveComplexChildReference<*, *, *, *>?
                        )
                    )
                }
            }

            @Suppress("UNCHECKED_CAST")
            val definition = this.definitionMapByIndex[newValue.type.index] as IsSubDefinition<Any, CX>?
                ?: throw DefNotFoundException("No def found for index ${newValue.type}")

            val prevValue = previousValue?.let {
                // Convert prev value to a basic value for Unit types
                // This is done so type changes can be checked in storage situations where actual value is not yet read
                if (it.value == Unit) {
                    val value: Any = when (definition) {
                        is IsEmbeddedValuesDefinition<*, *, *> -> (definition.dataModel as IsTypedValuesDataModel<*, *>).values(
                            null
                        ) { EmptyValueItems }
                        is IsListDefinition<*, *> -> emptyList<Any>()
                        is IsSetDefinition<*, *> -> emptySet<Any>()
                        is IsMapDefinition<*, *, *> -> emptyMap<Any, Any>()
                        else -> throw TypeException("Not supported complex multitype")
                    }
                    TypedValue(it.type, value)
                } else previousValue
            }

            definition.validateWithRef(
                prevValue?.value,
                newValue.value
            ) {
                @Suppress("UNCHECKED_CAST")
                refGetter() as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>?
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
        if (reader is IsYamlReader) {
            val token = reader.currentToken as? TokenWithType
                ?: throw ParseException("Expected an Token with YAML type tag which describes property type")

            val tokenType = token.type
            val type: E = when (tokenType) {
                is IndexedEnum<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    tokenType as E
                }
                is UnknownYamlTag -> {
                    this.typeEnum.resolve(tokenType.name)
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
                    throw ParseException("Expected a value at start, not ${it.name}")
                }

                val type = this.typeEnum.resolve(it.value as String)
                    ?: throw ParseException("Invalid multi type name ${it.value}")
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
        // Read the protobuf where the key tag is the type
        val key = ProtoBuf.readKey(reader)

        val type = this.typeEnum.resolve(key.tag.toUInt()) ?: throw ParseException("Unknown multi type index ${key.tag}")
        val def = this.definitionMapByIndex[type.index] ?: throw ParseException("Unknown multi type ${key.tag}")

        val value = if (def is IsEmbeddedObjectDefinition<*, *, *, *, *> && keepAsValues) {
            (def as IsEmbeddedObjectDefinition<*, *, *, CX, *>).readTransportBytesToValues(
                ProtoBuf.getLength(key.wireType, reader),
                reader,
                context
            )
        } else {
            def.readTransportBytes(
                ProtoBuf.getLength(key.wireType, reader),
                reader,
                context
            )
        }

        return TypedValue(type, value)
    }

    override fun calculateTransportByteLength(value: TypedValue<E, Any>, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0

        if (context is RequestContext) {
            context.collectInjectLevel(this) {
                this.typeRef(value.type, it as CanHaveComplexChildReference<*, *, *, *>)
            }
        }

        // stored as value below an index of the type id
        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMapByIndex[value.type.index] as IsSubDefinition<Any, CX>?
            ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        totalByteLength += def.calculateTransportByteLengthWithKey(
            value.type.index.toInt(),
            value.value,
            cacher,
            context
        )

        if (context is RequestContext) {
            context.closeInjectLevel(this)
        }

        return totalByteLength
    }

    override fun writeTransportBytes(
        value: TypedValue<E, Any>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        @Suppress("UNCHECKED_CAST")
        val def = this.definitionMapByIndex[value.type.index] as IsSubDefinition<Any, CX>?
            ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        def.writeTransportBytesWithKey(value.type.index.toInt(), value.value, cacheGetter, writer, context)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultiTypeDefinition<*, *>) return false

        if (required != other.required) return false
        if (final != other.final) return false
        if (typeIsFinal != other.typeIsFinal) return false
        if (definitionMap != other.definitionMap) {
            if (definitionMap.size != other.definitionMap.size) return false
            definitionMap.entries.zip(other.definitionMap.entries).map {
                if (it.first.key.index != it.second.key.index
                    || it.first.key.name != it.second.key.name
                    || it.first.value != it.second.value
                ) {
                    return false
                }
            }
        }

        return true
    }

    override fun hashCode(): Int {
        var result = required.hashCode()
        result = 31 * result + final.hashCode()
        result = 31 * result + definitionMap.hashCode()
        result = 31 * result + typeIsFinal.hashCode()
        return result
    }

    /** Resolve a reference from [reader] found on a [parentReference] */
    override fun resolveReference(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>?
    ): IsPropertyReference<Any, *, *> {
        val index = initIntByVar(reader)
        if (index != 0) throw UnexpectedValueException("Index in multi type reference other than 0 ($index) is not supported")
        val typeIndex = initUIntByVar(reader)
        return if (typeIndex == 0u) {
            @Suppress("UNCHECKED_CAST")
            this.anyTypeRef(
                parentReference as CanHaveComplexChildReference<TypedValue<E, *>, IsMultiTypeDefinition<E, *>, *, *>?
            ) as IsPropertyReference<Any, *, *>
        } else {
            val type = this.typeEnum.resolve(typeIndex) ?: throw UnexpectedValueException("Type $typeIndex is not known")
            typeRef(type, parentReference)
        }
    }

    override fun resolveReferenceFromStorage(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>?
    ): IsPropertyReference<Any, *, *> {
        val typeIndex = initUIntByVarWithExtraInfo(reader) { index, type ->
            if (type != TYPE.value) throw SerializationException("Expected TypedValue")
            index
        }
        return if (typeIndex == 0u) {
            @Suppress("UNCHECKED_CAST")
            this.anyTypeRef(
                parentReference as CanHaveComplexChildReference<TypedValue<E, *>, IsMultiTypeDefinition<E, *>, *, *>?
            ) as IsPropertyReference<Any, *, *>
        } else {
            val type = this.typeEnum.resolve(typeIndex)
                ?: throw UnexpectedValueException("Type $typeIndex is not known")
            typeRef(type, parentReference)
        }
    }

    /** Resolve a reference from [name] found on a [parentReference] */
    override fun resolveReferenceByName(
        name: String,
        parentReference: CanHaveComplexChildReference<*, *, *, *>?
    ) = when (name[0]) {
        '*' -> {
            if (name.length == 1) {
                @Suppress("UNCHECKED_CAST")
                MultiAnyTypeReference(
                    this,
                    parentReference as CanHaveComplexChildReference<TypedValue<E, *>, IsMultiTypeDefinition<E, *>, *, *>?
                ) as IsPropertyReference<Any, *, *>
            } else {
                val type = this.typeEnum.resolve(name.substring(1))
                    ?: throw UnexpectedValueException("Type ${name.substring(1)} is not known")
                typeRef(type, parentReference)
            }
        }
        else -> throw ParseException("Unknown Type type $name[0]")
    }

    object Model :
        ContextualDataModel<MultiTypeDefinition<*, *>, ObjectPropertyDefinitions<MultiTypeDefinition<*, *>>, ContainsDefinitionsContext, MultiTypeDefinitionContext>(
            contextTransformer = { MultiTypeDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<MultiTypeDefinition<*, *>>() {
                init {
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

                    add(4, "typeIsFinal", BooleanDefinition(default = true), MultiTypeDefinition<*, *>::typeIsFinal)

                    this.addDescriptorPropertyWrapperWrapper(5, "definitionMap")

                    @Suppress("UNCHECKED_CAST")
                    add(6, "default",
                        ContextualValueDefinition(
                            required = false,
                            contextTransformer = { context: MultiTypeDefinitionContext? ->
                                context?.definitionsContext
                            },
                            contextualResolver = { context: MultiTypeDefinitionContext? ->
                                context?.multiTypeDefinition ?: throw ContextNotFoundException()
                            }
                        ) as IsContextualEncodable<TypedValue<out IndexedEnum<*>, Any>, MultiTypeDefinitionContext>,
                        MultiTypeDefinition<*, *>::default
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<MultiTypeDefinition<*, *>>): MultiTypeDefinition<IndexedEnum<Any>, ContainsDefinitionsContext> {
            val definitionMap = convertMultiTypeDescriptors(
                values(5)
            )

            val typeOptions = definitionMap.keys.toTypedArray()

            val typeEnum = IndexedEnumDefinition(
                values(3)
            ) { typeOptions }

            return MultiTypeDefinition(
                required = values(1),
                final = values(2),
                typeEnum = typeEnum,
                typeIsFinal = values(4),
                definitionMap = definitionMap,
                default = values(6)
            )
        }
    }
}

class MultiTypeDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?
) : IsPropertyContext {
    var typeEnumName: String? = null

    var definitionMap: Map<IndexedEnum<Any>, IsSubDefinition<out Any, ContainsDefinitionsContext>>? = null

    private var _multiTypeDefinition: Lazy<MultiTypeDefinition<IndexedEnum<Any>, ContainsDefinitionsContext>> = lazy {
        val typeOptions = definitionMap?.keys?.toTypedArray() ?: throw ContextNotFoundException()

        val typeEnum = IndexedEnumDefinition(
            typeEnumName ?: throw ContextNotFoundException()
        ) { typeOptions }

        MultiTypeDefinition(
            typeEnum = typeEnum,
            definitionMap = this.definitionMap ?: throw ContextNotFoundException()
        )
    }

    val multiTypeDefinition: MultiTypeDefinition<IndexedEnum<Any>, ContainsDefinitionsContext> get() = this._multiTypeDefinition.value
}
