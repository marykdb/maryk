package maryk.core.models

import maryk.core.inject.Inject
import maryk.core.inject.InjectWithReference
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
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
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartDocument
import maryk.json.JsonToken.StartObject
import maryk.json.JsonToken.Stopped
import maryk.json.JsonToken.Value
import maryk.json.JsonWriter
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
abstract class AbstractDataModel<DO : Any, P : IsObjectPropertyDefinitions<DO>, V : AbstractValues<DO, P>, in CXI : IsPropertyContext, CX : IsPropertyContext> internal constructor(
    final override val properties: P
) : IsDataModelWithValues<DO, P, V> {
    /**
     * Write [values] for this ObjectDataModel to JSON
     * Optionally pass a [context] when needed for more complex property types
     */
    fun writeJson(
        values: V,
        context: CX? = null,
        pretty: Boolean = false
    ) = buildString {
        val writer = JsonWriter(pretty = pretty, ::append)
        writeJson(values, writer, context)
    }

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

                val injectionContext = Inject.Model.transformContext(context as RequestContext)
                Inject.Model.writeJson(value, writer, injectionContext)
            } else {
                definition.capture(context, value)
                writeJsonValue(definition, writer, value, context)
            }
        }
        writer.writeEndObject()
    }

    internal fun writeJsonValue(
        def: IsDefinitionWrapper<in Any, in Any, IsPropertyContext, DO>,
        writer: IsJsonLikeWriter,
        value: Any,
        context: CX?
    ) {
        writer.writeFieldName(def.name)
        def.definition.writeJsonValue(value, writer, context)
    }

    /**
     * Read JSON from [json] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    fun readJson(json: String, context: CX? = null): V {
        var i = 0
        val reader = JsonReader { json[i++] }
        return this.readJson(reader, context)
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
        if (reader.currentToken == StartDocument) {
            reader.nextToken()
        }

        return if (properties.isNotEmpty()) {
            if (reader.currentToken !is StartObject) {
                throw IllegalJsonOperation("Expected object at start of JSON, not ${reader.currentToken}")
            }

            val valueMap = MutableValueItems()
            reader.nextToken()
            walkJsonToRead(reader, valueMap, context)

            valueMap
        } else {
            reader.nextToken()
            MutableValueItems()
        }
    }

    internal open fun walkJsonToRead(
        reader: IsJsonLikeReader,
        values: MutableValueItems,
        context: CX?
    ) {
        walker@ do {
            val token = reader.currentToken
            when (token) {
                is FieldName -> {
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
                        val valueToken = reader.currentToken as? Value<*>
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
                            val inject = Inject.Model.readJson(reader, Inject.Model.transformContext(context as RequestContext))

                            values[definition.index] = inject
                        } else {
                            val readValue = if (definition is IsEmbeddedObjectDefinition<*, *, *, *>) {
                                @Suppress("UNCHECKED_CAST")
                                (definition as IsEmbeddedObjectDefinition<*, *, CX, *>).readJsonToValues(
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
        } while (token !is Stopped)
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
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        cacher: WriteCacheWriter,
        context: CX?
    ): Int {
        if (value == null) {
            return 0 // Skip null value in counting
        }

        // If it is an Inject it needs to be collected for protobuf since it cannot be encoded inline
        // Except if it is the InjectWithReference object in which it is encoded
        if (value is Inject<*, *> && this != InjectWithReference.Model) {
            if (context is RequestContext) {
                context.collectInjectLevel(this, definition::ref)
                context.collectInject(value)
            }

            return 0 // Don't count length of Inject values since they are encoded in Requests object
        } else if (
            context is RequestContext
            && (definition is IsEmbeddedObjectDefinition<*, *, *, *>
                    || definition is IsCollectionDefinition<*, *, *, *>
                    || definition is IsMapDefinition<*, *, *>)
        ) {
            // Collect inject level if value can contain sub values
            context.collectInjectLevel(this, definition::ref)
        }

        definition.capture(context, value)
        return definition.definition.calculateTransportByteLengthWithKey(definition.index.toInt(), value, cacher, context)
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
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        if (value == null) return // Skip empty values

        if (value is Inject<*, *> && this != InjectWithReference.Model) {
            return // Skip Inject values since they are encoded in Requests object
        }

        definition.capture(context, value)

        definition.definition.writeTransportBytesWithKey(definition.index.toInt(), value, cacheGetter, writer, context)
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    open fun readProtoBuf(length: Int, reader: () -> Byte, context: CX? = null): V {
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
            values[key.tag] =
                when (propertyDefinition) {
                    is IsEmbeddedObjectDefinition<Any, *, CX, *> ->
                        propertyDefinition.readTransportBytesToValues(
                            ProtoBuf.getLength(key.wireType, byteReader),
                            byteReader,
                            context
                        )
                    else ->
                        propertyDefinition.readTransportBytes(
                            ProtoBuf.getLength(key.wireType, byteReader),
                            byteReader,
                            context,
                            values[key.tag]
                        )
                }.also {
                    dataObjectPropertyDefinition.capture(context, it)
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
            } as T
        }

        return transform(value as TI)
    }
}
