package maryk.core.models.serializers

import maryk.core.inject.Inject
import maryk.core.inject.InjectWithReference
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsTypedDataModel
import maryk.core.models.IsValuesDataModel
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
import maryk.core.values.IsValueItems
import maryk.core.values.IsValues
import maryk.core.values.MutableValueItems
import maryk.core.values.ObjectValues
import maryk.core.values.Values
import maryk.json.IllegalJsonOperation
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

/** Serializer for DataModels */
open class DataModelSerializer<DO: Any, V: IsValues<DM>, DM: IsTypedDataModel<DO>, CX: IsPropertyContext>(
    val model: DM,
): IsDataModelSerializer<V, DM, CX> {
    /**
     * Write [values] for this DataModel to JSON
     * Optionally pass a [context] when needed for more complex property types
     */
    override fun writeJson(
        values: V,
        context: CX?,
        pretty: Boolean
    ) = buildString {
        val writer = JsonWriter(pretty = pretty, ::append)
        writeJson(values, writer, context)
    }

    /**
     * Write [values] for this DataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    override fun writeJson(
        values: V,
        writer: IsJsonLikeWriter,
        context: CX?
    ) {
        writer.writeStartObject()
        for ((index, value) in values) {
            val definition = model[index] ?: continue

            if (value is Inject<*, *>) {
                if (writer is YamlWriter) {
                    writer.writeFieldName(definition.name)
                    writer.writeTag("!:Inject")
                } else {
                    writer.writeFieldName("?${definition.name}")
                }

                val injectionContext = Inject.Serializer.transformContext(context as RequestContext)
                Inject.Serializer.writeObjectAsJson(value, writer, injectionContext)
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
    override fun readJson(json: String, context: CX?): V {
        var i = 0
        val reader = JsonReader { json[i++] }
        return this.readJson(reader, context)
    }

    /**
     * Read JSON from [reader] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    override fun readJson(reader: IsJsonLikeReader, context: CX?): V =
        createValues(context, readJsonToMap(reader, context))

    /**
     * Read JSON from [reader] to a Map
     * Optionally pass a [context] when needed to read more complex property types
     */
    open fun readJsonToMap(reader: IsJsonLikeReader, context: CX? = null): MutableValueItems {
        if (reader.currentToken == JsonToken.StartDocument) {
            reader.nextToken()
        }

        return if (model.isNotEmpty()) {
            if (reader.currentToken !is JsonToken.StartObject) {
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

                    val definition = model[fieldName]
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
                            val inject = Inject.Serializer.readJson(reader, Inject.Serializer.transformContext(context as RequestContext))

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
        } while (true)
    }

    override fun calculateProtoBufLength(values: V, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        for (definition in this.model) {
            val originalValue = values.original(definition.index)

            totalByteLength += this.protoBufLengthToAddForField(originalValue, definition, cacher, context)
        }

        if (context is RequestContext) {
            context.closeInjectLevel(this.model)
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
        if (value is Inject<*, *> && this.model != InjectWithReference) {
            if (context is RequestContext) {
                context.collectInjectLevel(this.model, definition::ref)
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
            context.collectInjectLevel(this.model, definition::ref)
        }

        definition.capture(context, value)
        return definition.definition.calculateTransportByteLengthWithKey(definition.index.toInt(), value, cacher, context)
    }

    override fun writeProtoBuf(values: V, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        for (definition in this.model) {
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

        if (value is Inject<*, *> && this.model != InjectWithReference) {
            return // Skip Inject values since they are encoded in Requests object
        }

        definition.capture(context, value)

        definition.definition.writeTransportBytesWithKey(definition.index.toInt(), value, cacheGetter, writer, context)
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    override fun readProtoBuf(length: Int, reader: () -> Byte, context: CX?): V =
        createValues(context, readProtoBufToMap(length, reader, context))

    /** Creates Values [V] from a [items] map */
    open fun createValues(context: CX?, items: IsValueItems): V {
        @Suppress("UNCHECKED_CAST")
        return when (this.model) {
            is IsObjectDataModel<*> ->
                ObjectValues(this.model, items, context as? RequestContext) as V
            is IsValuesDataModel ->
                Values(this.model, items, context as? RequestContext) as V
            else -> throw Exception("Unknown properties type ${this.model::class.simpleName}")
        }
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    internal fun readProtoBufToMap(length: Int, reader: () -> Byte, context: CX? = null): IsValueItems {
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
        val dataObjectPropertyDefinition = model[key.tag]
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
            val valueDef = model[index]!!
            throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
        }

        if (value == null) {
            return when {
                default != null -> default
                value !is TI -> {
                    val valueDef = model[index]!!
                    throw ParseException("Property '${valueDef.name}' with value '$value' cannot be null")
                }
                else -> value
            } as T
        }

        return transform(value as TI)
    }
}
