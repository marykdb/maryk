package maryk.core.models

import maryk.core.inject.Inject
import maryk.core.inject.InjectWithReference
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsByteTransportableValue
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.ProtoBufKey
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.AbstractValues
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonReader
import maryk.json.JsonToken
import maryk.json.TokenWithType
import maryk.lib.exceptions.ParseException
import maryk.yaml.IsYamlReader
import maryk.yaml.UnknownYamlTag
import maryk.yaml.YamlWriter

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the ObjectDataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractDataModel<DO : Any, P : AbstractPropertyDefinitions<DO>, V : AbstractValues<DO, *, P>, in CXI : IsPropertyContext, CX : IsPropertyContext> internal constructor(
    final override val properties: P
) : IsDataModelWithValues<DO, P, V> {

    /**
     * Write [values] for this ObjectDataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    open fun writeJson(
        values: V,
        writer: IsJsonLikeWriter,
        context: CX? = null
    ) {
        writer.writeStartObject()
        for ((index, value) in values) {
            val definition = properties[index] ?: continue

            if (value is Inject<*, *>) {
                if (writer is YamlWriter) {
                    writer.writeFieldName(definition.name)
                    writer.writeTag("!:Inject")
                } else {
                    writer.writeFieldName("?${definition.name}")
                }

                val injectionContext = Inject.transformContext(context as RequestContext)
                Inject.writeJson(value, writer, injectionContext)
            } else {
                definition.capture(context, value)
                writeJsonValue(definition, writer, value, context)
            }
        }
        writer.writeEndObject()
    }

    internal fun writeJsonValue(
        def: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        writer: IsJsonLikeWriter,
        value: Any,
        context: CX?
    ) {
        writer.writeFieldName(def.name)
        def.definition.writeJsonValue(value, writer, context)
    }

    /**
     * Read JSON from [reader] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    open fun readJson(reader: IsJsonLikeReader, context: CX? = null): V {
        return this.values(context as? RequestContext) {
            this@AbstractDataModel.readJsonToMap(reader, context)
        }
    }

    /**
     * Read JSON from [reader] to a Map
     * Optionally pass a [context] when needed to read more complex property types
     */
    open fun readJsonToMap(reader: IsJsonLikeReader, context: CX? = null): MutableValueItems {
        if (reader.currentToken == JsonToken.StartDocument) {
            reader.nextToken()
        }

        if (reader.currentToken !is JsonToken.StartObject) {
            throw IllegalJsonOperation("Expected object at start of JSON")
        }

        val valueMap = MutableValueItems()
        reader.nextToken()
        walkJsonToRead(reader, valueMap, context)

        return valueMap
    }

    internal open fun walkJsonToRead(
        reader: IsJsonLikeReader,
        values: MutableValueItems,
        context: CX?
    ) {
        walker@ do {
            val token = reader.currentToken
            when (token) {
                is JsonToken.FieldName -> {
                    var isInject = false

                    val fieldName = token.value?.let {
                        if (reader is JsonReader && it.startsWith("?")) {
                            isInject = true
                            it.substring(1)
                        } else {
                            it
                        }
                    } ?: throw ParseException("Empty field name not allowed in JSON")

                    val definition = properties[fieldName]
                    if (definition == null) {
                        reader.skipUntilNextField()
                        continue@walker
                    } else {
                        reader.nextToken()

                        // Skip null values
                        val valueToken = reader.currentToken as? JsonToken.Value<*>
                        if (valueToken != null && valueToken.value == null) {
                            reader.nextToken()
                            continue@walker
                        }

                        if (reader is IsYamlReader) {
                            reader.currentToken.let { yamlToken ->
                                if (yamlToken is TokenWithType) {
                                    yamlToken.type.let {
                                        if (it is UnknownYamlTag) {
                                            isInject = it.name == ":Inject"
                                        }
                                    }
                                }
                            }
                        }

                        if (isInject) {
                            val inject = Inject.readJson(reader, Inject.transformContext(context as RequestContext))

                            values[definition.index] = inject
                        } else {
                            val readValue = if (definition is IsEmbeddedObjectDefinition<*, *, *, *, *>) {
                                @Suppress("UNCHECKED_CAST")
                                (definition as IsEmbeddedObjectDefinition<*, *, *, CX, *>).readJsonToValues(
                                    reader,
                                    context
                                )
                            } else {
                                definition.readJson(reader, context)
                            }

                            values[definition.index] = readValue
                            definition.capture(context, readValue)
                        }
                    }
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is JsonToken.Stopped)
    }

    /**
     * Calculates the byte length for the DataObject contained in [values]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun calculateProtoBufLength(values: V, cacher: WriteCacheWriter, context: CX? = null): Int {
        var totalByteLength = 0
        for (definition in this.properties) {
            val originalValue = values.original(definition.index)

            totalByteLength += this.protoBufLengthToAddForField(originalValue, definition, cacher, context)
        }

        if (context is RequestContext) {
            context.closeInjectLevel(this)
        }

        return totalByteLength
    }

    /** Calculates length for the ProtoBuf field for [value] */
    protected open fun protoBufLengthToAddForField(
        value: Any?,
        definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        cacher: WriteCacheWriter,
        context: CX?
    ): Int {
        if (value == null) {
            return 0 // Skip null value in counting
        }

        // If it is inject it needs to be collected for protobuf since it cannot be encoded inline
        // Except if it is the InjectWithReference object in which it is encoded
        if (value is Inject<*, *> && this !is InjectWithReference.Companion) {
            if (context is RequestContext) {
                context.collectInjectLevel(this) { definition.ref(it) }
                context.collectInject(value)
            }

            return 0 // Don't count length of Inject values since they are encoded in Requests object
        } else if (
            context is RequestContext
            && (definition is IsEmbeddedObjectDefinition<*, *, *, *, *>
                    || definition is IsCollectionDefinition<*, *, *, *>
                    || definition is IsMapDefinition<*, *, *>)
        ) {
            // Collect inject level if value can contain sub values
            context.collectInjectLevel(this) { definition.ref(it) }
        }

        definition.capture(context, value)
        return definition.definition.calculateTransportByteLengthWithKey(definition.index, value, cacher, context)
    }

    /**
     * Write a ProtoBuf from a [values] with values to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeProtoBuf(values: V, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for (definition in this.properties) {
            val originalValue = values.original(definition.index)

            this.writeProtoBufField(originalValue, definition, cacheGetter, writer, context)
        }
    }

    /**
     * Writes a specific ProtoBuf field defined by [definition] with [value] to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal open fun writeProtoBufField(
        value: Any?,
        definition: IsPropertyDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        if (value == null) return // Skip empty values

        if (value is Inject<*, *> && this !is InjectWithReference.Companion) {
            return // Skip Inject values since they are encoded in Requests object
        }

        definition.capture(context, value)

        definition.definition.writeTransportBytesWithKey(definition.index, value, cacheGetter, writer, context)
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun readProtoBuf(length: Int, reader: () -> Byte, context: CX? = null): V {
        return this.values(context as? RequestContext) {
            this@AbstractDataModel.readProtoBufToMap(length, reader, context)
        }
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    private fun readProtoBufToMap(length: Int, reader: () -> Byte, context: CX? = null): IsValueItems {
        val valueMap = MutableValueItems()
        var byteCounter = 1

        val byteReader = {
            byteCounter++
            reader()
        }

        while (byteCounter < length) {
            readProtoBufField(
                valueMap,
                ProtoBuf.readKey(byteReader),
                byteReader,
                context
            )
        }

        return valueMap
    }

    /**
     * Read a single field of [key] from [byteReader] into [values]
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    private fun readProtoBufField(values: MutableValueItems, key: ProtoBufKey, byteReader: () -> Byte, context: CX?) {
        val dataObjectPropertyDefinition = properties[key.tag]
        val propertyDefinition = dataObjectPropertyDefinition?.definition

        if (propertyDefinition == null) {
            ProtoBuf.skipField(key.wireType, byteReader)
        } else {
            when (propertyDefinition) {
                is IsByteTransportableValue<*, CX> -> {
                    values[key.tag] = if (propertyDefinition is IsEmbeddedObjectDefinition<*, *, *, *, *>) {
                        @Suppress("UNCHECKED_CAST")
                        (propertyDefinition as IsEmbeddedObjectDefinition<*, *, *, CX, *>).readTransportBytesToValues(
                            ProtoBuf.getLength(key.wireType, byteReader),
                            byteReader,
                            context
                        )
                    } else {
                        propertyDefinition.readTransportBytes(
                            ProtoBuf.getLength(key.wireType, byteReader),
                            byteReader,
                            context
                        )
                    }.also {
                        dataObjectPropertyDefinition.capture(context, it)
                    }
                }
                is IsByteTransportableCollection<out Any, *, CX> -> {
                    when {
                        propertyDefinition.isPacked(context, key.wireType) -> {
                            @Suppress("UNCHECKED_CAST")
                            val collection = propertyDefinition.readPackedCollectionTransportBytes(
                                ProtoBuf.getLength(key.wireType, byteReader),
                                byteReader,
                                context
                            ) as MutableCollection<Any>

                            dataObjectPropertyDefinition.capture(context, collection)

                            @Suppress("UNCHECKED_CAST")
                            when {
                                values.contains(key.tag) -> (values[key.tag] as MutableCollection<Any>).addAll(
                                    collection
                                )
                                else -> values[key.tag] = collection
                            }
                        }
                        else -> {
                            val value = propertyDefinition.readCollectionTransportBytes(
                                ProtoBuf.getLength(key.wireType, byteReader),
                                byteReader,
                                context
                            )
                            @Suppress("UNCHECKED_CAST")
                            val collection = when {
                                values.contains(key.tag) -> values[key.tag]
                                else -> propertyDefinition.newMutableCollection(context).also {
                                    values[key.tag] = it
                                }
                            } as MutableCollection<Any>

                            collection += value
                            dataObjectPropertyDefinition.capture(context, collection)
                        }
                    }
                }
                is IsByteTransportableMap<out Any, out Any, CX> -> {
                    ProtoBuf.getLength(key.wireType, byteReader)
                    val value = propertyDefinition.readMapTransportBytes(
                        byteReader,
                        context
                    )
                    if (values.contains(key.tag)) {
                        @Suppress("UNCHECKED_CAST")
                        val map = values[key.tag] as MutableMap<Any, Any>
                        map[value.first] = value.second
                    } else {
                        values[key.tag] = mutableMapOf(value).also {
                            dataObjectPropertyDefinition.capture(context, it)
                        }
                    }
                }
                else -> throw ParseException("Unknown property type for ${dataObjectPropertyDefinition.name}")
            }
        }
    }

    /**
     * Utility method to check and map a value to a constructor property
     */
    protected inline fun <reified T, reified TI> Map<UInt, *>.transform(
        index: UInt,
        transform: (TI) -> T,
        default: T? = null
    ): T {
        val value: Any? = this[index]

        if (value !is TI?) {
            val valueDef = this@AbstractDataModel.properties[index]!!
            throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
        }

        if (value == null) {
            return when {
                default != null -> default
                value !is TI -> {
                    val valueDef = this@AbstractDataModel.properties[index]!!
                    throw ParseException("Property '${valueDef.name}' with value '$value' cannot be null")
                }
                else -> value
            }
        }

        return transform(value as TI)
    }
}
