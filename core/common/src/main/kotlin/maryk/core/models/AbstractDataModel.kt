package maryk.core.models

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.ValueMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsByteTransportableValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionsCollectionDefinition
import maryk.core.properties.definitions.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.ProtoBufKey
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IllegalJsonOperation
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the DataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractDataModel<DO: Any, P: PropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext> internal constructor(
    override val properties: P
) : IsDataModel<DO, P> {
    /** For quick notation to return [T] that operates with [runner] on Properties */
    fun <T: Any> props(
        runner: P.() -> T
    ) = runner(this.properties)

    /** Create a ValueMap with given [createMap] function */
    fun map(createMap: P.() -> Map<Int, Any?>) = ValueMap(this, createMap(this.properties))

    /**
     * Get property reference fetcher of this DataModel with [referenceGetter]
     * Optionally pass an already resolved [parent]
     * For Strongly typed reference notation
     */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
        referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) -> IsPropertyReference<T, W>
    ): IsPropertyReference<T, W> {
        return referenceGetter(this.properties)(parent)
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> ref(parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null, propertyDefinitionGetter: P.()-> W): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    /**
     * To get a top level reference on a model by passing a [propertyDefinitionGetter] from its defined Properties
     * Optionally pass an already resolved [parent]
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *, *>> graph(
        parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
        propertyDefinitionGetter: P.()-> W
    ): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    override fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        createValidationUmbrellaException(refGetter) { addException ->
            for (it in this.properties) {
                try {
                    it.validate(
                        newValue = it.getPropertyAndSerialize(dataObject, null),
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }

    override fun validate(map: ValueMap<DO, P>, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        createValidationUmbrellaException(refGetter) { addException ->
            for ((key, value) in map) {
                val definition = properties.getDefinition(key) ?: continue
                try {
                    definition.validate(
                        newValue = value,
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }

    /**
     * Write an [obj] of this DataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    open fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: CX? = null) {
        writer.writeStartObject()
        for (definition in this.properties) {
            val value = definition.getPropertyAndSerialize(obj, context) ?: continue

            definition.capture(context, value)

            writeJsonValue(definition, writer, value, context)
        }
        writer.writeEndObject()
    }

    /**
     * Write an [map] with values for this DataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    open fun writeJson(map: ValueMap<DO, P>, writer: IsJsonLikeWriter, context: CX? = null) {
        writer.writeStartObject()
        for ((key, value) in map) {
            if (value == null) continue

            val definition = properties.getDefinition(key) ?: continue

            definition.capture(context, value)

            writeJsonValue(definition, writer, value, context)
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
    open fun readJson(reader: IsJsonLikeReader, context: CX? = null): ValueMap<DO, P> {
        if (reader.currentToken == JsonToken.StartDocument){
            reader.nextToken()
        }

        if (reader.currentToken !is JsonToken.StartObject) {
            throw IllegalJsonOperation("Expected object at start of JSON")
        }

        val valueMap: MutableMap<Int, Any> = mutableMapOf()
        reader.nextToken()
        walkJsonToRead(reader, valueMap, context)

        return this.map {
            valueMap
        }
    }

    internal open fun walkJsonToRead(
        reader: IsJsonLikeReader,
        valueMap: MutableMap<Int, Any>,
        context: CX?
    ) {
        walker@ do {
            val token = reader.currentToken
            when (token) {
                is JsonToken.FieldName -> {
                    val value = token.value ?: throw ParseException("Empty field name not allowed in JSON")

                    val definition = properties.getDefinition(value)
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

                        definition.definition.readJson(reader, context).also {
                            valueMap[definition.index] = it
                            definition.capture(context, it)
                        }
                    }
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is JsonToken.Stopped)
    }

    /**
     * Calculates the byte length for the DataObject contained in [map]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun calculateProtoBufLength(map: ValueMap<DO, P>, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for ((key, value) in map) {
            if (value == null) continue // continue on empty values

            val def = properties.getDefinition(key) ?: continue

            def.capture(context, value)

            totalByteLength += def.definition.calculateTransportByteLengthWithKey(def.index, value, cacher, context)
        }
        return totalByteLength
    }

    /**
     * Calculates the byte length for [dataObject]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun calculateProtoBufLength(dataObject: DO, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for (definition in this.properties) {
            val value = definition.getPropertyAndSerialize(dataObject, context) ?: continue

            definition.capture(context, value)

            totalByteLength += definition.definition.calculateTransportByteLengthWithKey(definition.index, value, cacher, context)
        }
        return totalByteLength
    }

    /**
     * Write a ProtoBuf from a [map] with values to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun writeProtoBuf(map: ValueMap<DO, P>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for ((key, value) in map) {
            if (value == null) continue // skip empty values

            val definition = properties.getDefinition(key) ?: continue

            definition.capture(context, value)

            definition.definition.writeTransportBytesWithKey(definition.index, value, cacheGetter, writer, context)
        }
    }

    /**
     * Write a ProtoBuf from a [dataObject] to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal fun writeProtoBuf(dataObject: DO, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for (definition in this.properties) {
            val value = definition.getPropertyAndSerialize(dataObject, context) ?: continue

            definition.capture(context, value)

            definition.definition.writeTransportBytesWithKey(definition.index, value, cacheGetter, writer, context)
        }
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    internal fun readProtoBuf(length: Int, reader: () -> Byte, context: CX? = null): ValueMap<DO, P> {
        val valueMap: MutableMap<Int, Any> = mutableMapOf()
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

        return this.map {
            valueMap
        }
    }

    /**
     * Read a single field of [key] from [byteReader] into [valueMap]
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    private fun readProtoBufField(valueMap: MutableMap<Int, Any>, key: ProtoBufKey, byteReader: () -> Byte, context: CX?) {
        val dataObjectPropertyDefinition = properties.getDefinition(key.tag)
        val propertyDefinition = dataObjectPropertyDefinition?.definition

        if (propertyDefinition == null) {
            ProtoBuf.skipField(key.wireType, byteReader)
        } else {
            when (propertyDefinition) {
                is IsByteTransportableValue<*, CX> -> valueMap[key.tag] = propertyDefinition.readTransportBytes(
                    ProtoBuf.getLength(key.wireType, byteReader),
                    byteReader,
                    context
                ).also {
                    dataObjectPropertyDefinition.capture(context, it)
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
                                valueMap.contains(key.tag) -> (valueMap[key.tag] as MutableCollection<Any>).addAll(collection)
                                else -> valueMap[key.tag] = collection
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
                                valueMap.contains(key.tag) -> valueMap[key.tag]
                                else -> propertyDefinition.newMutableCollection(context).also {
                                    valueMap[key.tag] = it
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
                    if (valueMap.contains(key.tag)) {
                        @Suppress("UNCHECKED_CAST")
                        val map = valueMap[key.tag] as MutableMap<Any, Any>
                        map[value.first] = value.second
                    } else {
                        valueMap[key.tag] = mutableMapOf(value).also {
                            dataObjectPropertyDefinition.capture(context, it)
                        }
                    }
                }
                else -> throw ParseException("Unknown property type for ${dataObjectPropertyDefinition.name}")
            }
        }
    }

    /** Transform [context] into context specific to DataModel. Override for specific implementation */
    @Suppress("UNCHECKED_CAST")
    internal open fun transformContext(context: CXI?): CX?  = context as CX?

    /**
     * Utility method to check and map a value to a constructor property
     */
    protected inline fun <reified T, reified TI> Map<Int, *>.transform(index: Int, transform: (TI) -> T, default: T? = null): T {
        val value: Any? = this[index]

        if (value !is TI?) {
            val valueDef = this@AbstractDataModel.properties.getDefinition(index)!!
            throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
        }

        if (value == null) {
            return when {
                default != null -> default
                value !is TI -> {
                    val valueDef = this@AbstractDataModel.properties.getDefinition(index)!!
                    throw ParseException("Property '${valueDef.name}' with value '$value' cannot be null")
                }
                else -> value
            }
        }

        return transform(value as TI)
    }

    internal companion object {
        internal fun <DO: DataModel<*, *>> addName(definitions: PropertyDefinitions<DO>, getter: (DO) -> String) {
            definitions.add(0, "name", StringDefinition(), getter)
        }

        internal fun <DO: DataModel<*, *>> addProperties(definitions: PropertyDefinitions<DO>): PropertyDefinitionsCollectionDefinitionWrapper<DO> {
            val wrapper = PropertyDefinitionsCollectionDefinitionWrapper<DO>(1, "properties", PropertyDefinitionsCollectionDefinition(
                capturer = { context, propDefs ->
                    context?.apply {
                        this.propertyDefinitions = propDefs
                    } ?: ContextNotFoundException()
                }
            )) {
                @Suppress("UNCHECKED_CAST")
                it.properties as PropertyDefinitions<Any>
            }

            definitions.addSingle(wrapper)
            return wrapper
        }
    }
}
