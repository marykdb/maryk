package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.exceptions.SerializationException
import maryk.core.exceptions.TypeException
import maryk.core.exceptions.UnexpectedValueException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initUIntByVar
import maryk.core.extensions.bytes.initUIntByVarWithExtraInfo
import maryk.core.models.values
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.enum.IsIndexedEnumDefinition
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.exceptions.AlreadySetException
import maryk.core.properties.references.AnyOutPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.ReferenceType.TYPE
import maryk.core.properties.references.TypeReference
import maryk.core.properties.references.TypedValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.EmptyValueItems
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonToken.Value
import maryk.json.JsonWriter
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/** Defines a multi type definition */
interface IsMultiTypeDefinition<E : TypeEnum<T>, T: Any, in CX : IsPropertyContext> :
    IsValueDefinition<TypedValue<E, T>, CX>,
    IsSerializablePropertyDefinition<TypedValue<E, T>, CX>,
    IsTransportablePropertyDefinitionType<TypedValue<E, T>>,
    HasDefaultValueDefinition<TypedValue<E, T>>,
    IsUsableInMapValue<TypedValue<E, T>, CX> {
    val typeIsFinal: Boolean
    val typeEnum: IsIndexedEnumDefinition<E>

    /** Get definition by [index] */
    fun definition(index: UInt): IsSubDefinition<out Any, CX>?

    /** Get definition by [type] */
    @Suppress("UNCHECKED_CAST")
    fun definition(type: E) = definition(type.index) as IsSubDefinition<T, CX>?

    /** Override to control if embedded objects need to be returned as values to support Inject */
    fun keepAsValues(): Boolean = false

    /**
     * Creates a reference referring to a value of [type] of multi type below [parentReference]
     * so reference can be strongly typed
     */
    fun typedValueRef(type: E, parentReference: CanHaveComplexChildReference<*, *, *, *>?) =
        TypedValueReference(type, this, parentReference)

    /** Creates a reference referring to any type of multi type below [parentReference] */
    @Suppress("UNCHECKED_CAST")
    fun typeRef(parentReference: AnyOutPropertyReference? = null) =
        TypeReference(
            this,
            parentReference as CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?
        )

    /** Resolve a reference from [reader] found on a [parentReference] */
    fun resolveReference(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *> {
        val index = initIntByVar(reader)
        if (index != 0) throw UnexpectedValueException("Index in multi type reference other than 0 ($index) is not supported")
        val typeIndex = initUIntByVar(reader)
        @Suppress("UNCHECKED_CAST")
        return if (typeIndex == 0u) {
            this.typeRef(
                parentReference as CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?
            ) as IsPropertyReference<Any, *, *>
        } else {
            val type = this.typeEnum.resolve(typeIndex) ?: throw UnexpectedValueException("Type $typeIndex is not known")
            typedValueRef(type, parentReference) as IsPropertyReference<Any, *, *>
        }
    }

    /** Resolve a stored reference from [reader] found on a [parentReference] */
    fun resolveReferenceFromStorage(
        reader: () -> Byte,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ): IsPropertyReference<Any, *, *> {
        val typeIndex = initUIntByVarWithExtraInfo(reader) { index, type ->
            if (type != TYPE.value) throw SerializationException("Expected TypedValue")
            index
        }
        @Suppress("UNCHECKED_CAST")
        return if (typeIndex == 0u) {
            this.typeRef(
                parentReference as CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?
            ) as IsPropertyReference<Any, *, *>
        } else {
            val type = this.typeEnum.resolve(typeIndex)
                ?: throw UnexpectedValueException("Type $typeIndex is not known")
            typedValueRef(type, parentReference) as IsPropertyReference<Any, *, *>
        }
    }

    /** Resolve a reference from [name] found on a [parentReference] */
    fun resolveReferenceByName(
        name: String,
        parentReference: CanHaveComplexChildReference<*, *, *, *>? = null
    ) = when (name[0]) {
        '*' -> {
            if (name.length == 1) {
                @Suppress("UNCHECKED_CAST")
                TypeReference(
                    this,
                    parentReference as CanHaveComplexChildReference<TypedValue<E, T>, IsMultiTypeDefinition<E, T, *>, *, *>?
                ) as IsPropertyReference<Any, *, *>
            } else {
                val type = this.typeEnum.resolve(name.substring(1))
                    ?: throw UnexpectedValueException("Type ${name.substring(1)} is not known")
                @Suppress("UNCHECKED_CAST")
                typedValueRef(type, parentReference) as IsPropertyReference<Any, *, *>
            }
        }
        else -> throw ParseException("Unknown Type type $name[0]")
    }

    override fun asString(value: TypedValue<E, T>, context: CX?): String {
        var string = ""
        this.writeJsonValue(value, JsonWriter {
            string += it
        }, context)
        return string
    }

    override fun fromString(string: String, context: CX?): TypedValue<E, T> {
        val stringIterator = string.iterator()
        return this.readJson(JsonReader { stringIterator.nextChar() }, context)
    }

    override fun validateWithRef(
        previousValue: TypedValue<E, T>?,
        newValue: TypedValue<E, T>?,
        refGetter: () -> IsPropertyReference<TypedValue<E, T>, IsPropertyDefinition<TypedValue<E, T>>, *>?
    ) {
        super<IsValueDefinition>.validateWithRef(previousValue, newValue, refGetter)
        if (newValue != null) {
            if (this.typeIsFinal && previousValue != null) {
                if (newValue.type != previousValue.type) {
                    throw AlreadySetException(
                        this.typedValueRef(
                            newValue.type,
                            refGetter() as CanHaveComplexChildReference<*, *, *, *>?
                        )
                    )
                }
            }

            val definition = this.definition(newValue.type)
                ?: throw DefNotFoundException("No def found for index ${newValue.type}")

            val prevValue = previousValue?.let {
                // Convert prev value to a basic value for Unit types
                // This is done so type changes can be checked in storage situations where actual value is not yet read
                if (it.value == Unit) {
                    @Suppress("UNCHECKED_CAST")
                    val value: T = when (definition) {
                        is IsEmbeddedValuesDefinition<*, *> ->
                            definition.dataModel.values(null) { EmptyValueItems } as T
                        is IsListDefinition<*, *> -> emptyList<Any>() as T
                        is IsSetDefinition<*, *> -> emptySet<Any>() as T
                        is IsMapDefinition<*, *, *> -> emptyMap<Any, Any>() as T
                        else -> throw TypeException("Not supported complex multi type")
                    }
                    TypedValue(it.type, value)
                } else previousValue
            }

            definition.validateWithRef(
                prevValue?.value,
                newValue.value
            ) {
                @Suppress("UNCHECKED_CAST")
                refGetter() as IsPropertyReference<T, IsPropertyDefinition<T>, *>?
            }
        }
    }

    override fun writeJsonValue(value: TypedValue<E, T>, writer: IsJsonLikeWriter, context: CX?) {
        val definition = this.definition(value.type)
            ?: throw DefNotFoundException("No def found for index ${value.type.name}")

        if (writer is YamlWriter) {
            if (value.type !is IsCoreEnum) {
                writer.writeTag("!${value.type.name}(${value.type.index})")
            } else {
                writer.writeTag("!${value.type.name}")
            }
            definition.writeJsonValue(value.value, writer, context)
        } else {
            writer.writeStartArray()
            if (value.type !is IsCoreEnum) {
                writer.writeString("${value.type.name}(${value.type.index})")
            } else {
                writer.writeString(value.type.name)
            }

            definition.writeJsonValue(value.value, writer, context)
            writer.writeEndArray()
        }
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): TypedValue<E, T> {
        if (reader is IsYamlReader) {
            val token = reader.currentToken as? TokenWithType
                ?: throw ParseException("Expected an Token with YAML type tag which describes property type, not: ${reader.currentToken}")

            val type: E = when (val tokenType = token.type) {
                is IndexedEnum -> {
                    @Suppress("UNCHECKED_CAST")
                    tokenType as E
                }
                is UnknownYamlTag -> {
                    this.typeEnum.resolve(tokenType.name)
                        ?: throw DefNotFoundException("Unknown type ${tokenType.name}")
                }
                else -> throw ParseException("Unknown tag type for $tokenType")
            }

            val definition = this.definition(type)
                ?: throw DefNotFoundException("No definition for type $type")

            val value = if (definition is IsEmbeddedObjectDefinition<*, *, CX, *> && this.keepAsValues()) {
                @Suppress("UNCHECKED_CAST")
                definition.readJsonToValues(reader, context) as T
            } else definition.readJson(reader, context)


            return TypedValue(type, value)
        } else {
            reader.nextToken().let {
                if (it !is Value<*>) {
                    throw ParseException("Expected a value at start, not ${it.name}")
                }
                val type: E = this.typeEnum.resolve(it.value as String)
                    ?: throw ParseException("Invalid multi type name ${it.value}")
                val definition = this.definition(type)
                    ?: throw DefNotFoundException("Unknown multi type index ${type.index}")

                reader.nextToken()
                val value = if (definition is IsEmbeddedObjectDefinition<*, *, CX, *> && this.keepAsValues()) {
                    @Suppress("UNCHECKED_CAST")
                    definition.readJsonToValues(reader, context) as T
                } else definition.readJson(reader, context)

                reader.nextToken() // skip end object

                return TypedValue(type, value)
            }
        }
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: TypedValue<E, T>?
    ): TypedValue<E, T> {
        var lengthToGo = length
        var value: T? = earlierValue?.value

        val wrappedReader = {
            lengthToGo--
            reader()
        }

        var type: E? = null

        while (lengthToGo > 0) {
            // Read the proto buf where the key tag is the type
            val key = ProtoBuf.readKey(wrappedReader)

            val newType = this.typeEnum.resolve(key.tag) ?: throw ParseException("Unknown multi type index ${key.tag}")

            type = when(type) {
                null -> newType
                newType -> type
                else -> throw ParseException("If multiple type values encoded they should be all of the same type")
            }

            val def = this.definition(type)
                ?: throw ParseException("Unknown multi type ${key.tag}")

            value = if (def is IsEmbeddedObjectDefinition<*, *, CX, *> && this.keepAsValues()) {
                @Suppress("UNCHECKED_CAST")
                def.readTransportBytesToValues(
                    ProtoBuf.getLength(key.wireType, wrappedReader),
                    wrappedReader,
                    context
                ) as T
            } else {
                def.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, wrappedReader),
                    wrappedReader,
                    context,
                    value
                )
            }
        }

        return TypedValue(
            type ?: throw ParseException("Multi type type cannot be null"),
            value ?: throw ParseException("Multi type value cannot be null")
        )
    }

    override fun calculateTransportByteLength(value: TypedValue<E, T>, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0

        if (context is RequestContext) {
            context.collectInjectLevel(this) {
                @Suppress("UNCHECKED_CAST")
                this.typedValueRef(value.type, it as CanHaveComplexChildReference<*, *, *, *>) as IsPropertyReference<in Any, *, *>
            }
        }

        // stored as value below an index of the type id
        val def = this.definition(value.type)
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
        value: TypedValue<E, T>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        val def = this.definition(value.type)
            ?: throw DefNotFoundException("Definition ${value.type} not found on Multi type")
        def.writeTransportBytesWithKey(value.type.index.toInt(), value.value, cacheGetter, writer, context)
    }

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null
}
