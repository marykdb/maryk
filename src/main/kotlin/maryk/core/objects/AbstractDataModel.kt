package maryk.core.objects

import maryk.core.json.IllegalJsonOperation
import maryk.core.json.IsJsonLikeReader
import maryk.core.json.IsJsonLikeWriter
import maryk.core.json.JsonToken
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsByteTransportableValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.PropertyDefinitionsCollectionDefinition
import maryk.core.properties.definitions.PropertyDefinitionsCollectionDefinitionWrapper
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.ProtoBufKey
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * A Data Model for converting and validating DataObjects. The [properties] contain all the property definitions for
 * this Model. [DO] is the type of DataObjects described by this model and [CX] the context to be used on the properties
 * to read and write. [CXI] is the input Context for properties. This can be different because the DataModel can create
 * its own context by transforming the given context.
 */
abstract class AbstractDataModel<DO: Any, out P: PropertyDefinitions<DO>, in CXI: IsPropertyContext, CX: IsPropertyContext>(
    override val properties: P
) : IsDataModel<DO> {
    /** Creates a Data Object by [map] */
    abstract operator fun invoke(map: Map<Int, *>): DO

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
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *>> ref(parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null, propertyDefinitionGetter: P.()-> W): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    override fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        createValidationUmbrellaException(refGetter) { addException ->
            for (it in this.properties) {
                try {
                    it.validate(
                        newValue = it.getter(dataObject),
                        parentRefFactory = refGetter
                    )
                } catch (e: ValidationException) {
                    addException(e)
                }
            }
        }
    }

    override fun validate(map: Map<Int, Any>, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
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
    fun writeJson(obj: DO, writer: IsJsonLikeWriter, context: CX? = null) {
        writer.writeStartObject()
        for (def in this.properties) {
            val name = def.name
            val value = def.getter(obj) ?: continue

            writer.writeFieldName(name)

            def.definition.writeJsonValue(value, writer, context)
        }
        writer.writeEndObject()
    }

    /**
     * Write an [map] with values for this DataModel to JSON with [writer]
     * Optionally pass a [context] when needed for more complex property types
     */
    fun writeJson(map: Map<Int, Any>, writer: IsJsonLikeWriter, context: CX? = null) {
        writer.writeStartObject()
        for ((key, value) in map) {
            val def = properties.getDefinition(key) ?: continue
            val name = def.name

            writer.writeFieldName(name)
            def.definition.writeJsonValue(value, writer, context)
        }
        writer.writeEndObject()
    }

    /**
     * Read JSON from [reader] to a Map with values
     * Optionally pass a [context] when needed to read more complex property types
     */
    fun readJson(reader: IsJsonLikeReader, context: CX? = null): Map<Int, Any> {
        if (reader.currentToken == JsonToken.StartJSON){
            reader.nextToken()
        }

        if (reader.currentToken != JsonToken.StartObject) {
            throw IllegalJsonOperation("Expected object at start of json")
        }

        val valueMap: MutableMap<Int, Any> = mutableMapOf()
        reader.nextToken()
        walker@ do {
            val token = reader.currentToken
            when (token) {
                JsonToken.FieldName -> {
                    val definition = properties.getDefinition(reader.lastValue)
                    if (definition == null) {
                        reader.skipUntilNextField()
                        continue@walker
                    } else {
                        reader.nextToken()

                        valueMap[definition.index] = definition.definition.readJson(reader, context)
                    }
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is JsonToken.Stopped)

        return valueMap
    }

    /**
     * Read JSON from [reader] to an object of this DataModel
     * Optionally pass a [context] when needed to read more complex property types
     */
    fun readJsonToObject(reader: IsJsonLikeReader, context: CX? = null) = this(this.readJson(reader, context))

    /**
     * Calculates the byte length for the DataObject contained in [map]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun calculateProtoBufLength(map: Map<Int, Any>, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for ((key, value) in map) {
            val def = properties.getDefinition(key) ?: continue
            totalByteLength += def.definition.calculateTransportByteLengthWithKey(def.index, value, cacher, context)
        }
        return totalByteLength
    }

    /**
     * Calculates the byte length for [dataObject]
     * The [cacher] caches any values needed to write later.
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun calculateProtoBufLength(dataObject: DO, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for (def in this.properties) {
            val value = def.getter(dataObject) ?: continue
            totalByteLength += def.definition.calculateTransportByteLengthWithKey(def.index, value, cacher, context)
        }
        return totalByteLength
    }

    /**
     * Write a ProtoBuf from a [map] with values to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeProtoBuf(map: Map<Int, Any>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for ((key, value) in map) {
            val def = properties.getDefinition(key) ?: continue
            def.definition.writeTransportBytesWithKey(def.index, value, cacheGetter, writer, context)
        }
    }

    /**
     * Write a ProtoBuf from a [dataObject] to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    fun writeProtoBuf(dataObject: DO, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for (def in this.properties) {
            val value = def.getter(dataObject) ?: continue
            def.definition.writeTransportBytesWithKey(def.index, value, cacheGetter, writer, context)
        }
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun readProtoBuf(length: Int, reader: () -> Byte, context: CX? = null): Map<Int, Any> {
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

        return valueMap
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a DataObject
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun readProtoBufToObject(length: Int, reader: () -> Byte, context: CX? = null) = this(this.readProtoBuf(length, reader, context))

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
                )
                is IsByteTransportableCollection<out Any, *, CX> -> {
                    when {
                        propertyDefinition.isPacked(context, key.wireType) -> {
                            @Suppress("UNCHECKED_CAST")
                            val collection = propertyDefinition.readPackedCollectionTransportBytes(
                                ProtoBuf.getLength(key.wireType, byteReader),
                                byteReader,
                                context
                            ) as MutableCollection<Any>
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
                        valueMap[key.tag] = mutableMapOf(value)
                    }
                }
                else -> throw ParseException("Unknown property type for ${dataObjectPropertyDefinition.name}")
            }
        }
    }

    /** Transform [context] into context specific to DataModel. Override for specific implementation */
    @Suppress("UNCHECKED_CAST")
    open fun transformContext(context: CXI?): CX?  = context as CX?

    internal companion object {
        internal fun <DO: DataModel<out Any, PropertyDefinitions<out Any>>> addName(definitions: PropertyDefinitions<DO>, getter: (DO) -> String) {
            definitions.add(0, "name", StringDefinition(), getter)
        }

        internal fun <DO: DataModel<out Any, PropertyDefinitions<out Any>>> addProperties(definitions: PropertyDefinitions<DO>) {
            definitions.addSingle(
                PropertyDefinitionsCollectionDefinitionWrapper(1, "properties", PropertyDefinitionsCollectionDefinition(
                    capturer = { context, propDefs -> context!!.propertyDefinitions = propDefs }
                )
                ) {
                    @Suppress("UNCHECKED_CAST")
                    it.properties as PropertyDefinitions<Any>
                }
            )

        }
    }
}
