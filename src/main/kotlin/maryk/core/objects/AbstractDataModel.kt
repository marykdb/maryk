package maryk.core.objects

import maryk.core.json.IllegalJsonOperation
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsByteTransportableMap
import maryk.core.properties.definitions.IsByteTransportableValue
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
 * A Data Model for converting and validating DataObjects
 * @param <DO> Type of DataObject which is modeled
 *
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 * @param CX: Type of context object
 */
abstract class AbstractDataModel<DO: Any, out P: PropertyDefinitions<DO>, CXI: IsPropertyContext, CX: IsPropertyContext>(
        val properties: P
) : IsDataModel<DO> {
    private val indexToDefinition: Map<Int, IsPropertyDefinitionWrapper<Any, CX, DO>>
    private val nameToDefinition: Map<String, IsPropertyDefinitionWrapper<Any, CX, DO>>

    @Suppress("UNCHECKED_CAST")
    val definitions: List<IsPropertyDefinitionWrapper<Any, CX, DO>> = properties.__allProperties as List<IsPropertyDefinitionWrapper<Any, CX, DO>>

    init {
        indexToDefinition = mutableMapOf()
        nameToDefinition = mutableMapOf()

        this.definitions.forEach { def ->
            require(def.index in (0..Short.MAX_VALUE), { "${def.index} for ${def.name} is outside range $(0..Short.MAX_VALUE)" })
            require(indexToDefinition[def.index] == null, { "Duplicate index ${def.index} for ${def.name} and ${indexToDefinition[def.index]?.name}" })
            indexToDefinition[def.index] = def
            nameToDefinition[def.name] = def
        }
    }

    /** Creates a Data Object by map
     * @param map with index mapped to value
     * @return newly created Data Object from map
     */
    abstract operator fun invoke(map: Map<Int, *>): DO

    override fun getDefinition(name: String) = nameToDefinition[name]
    override fun getDefinition(index: Int) = indexToDefinition[index]

    override fun getPropertyGetter(name: String) = nameToDefinition[name]?.getter
    override fun getPropertyGetter(index: Int) = indexToDefinition[index]?.getter

    /** For quick notation to fetch property references below submodels
     * @param referenceGetter The sub getter to fetch a reference
     * @return a reference to property
     */
    operator fun <T: Any, W: IsPropertyDefinition<T>> invoke(
            parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null,
            referenceGetter: P.() ->
            (IsPropertyReference<out Any, IsPropertyDefinition<*>>?) ->
            IsPropertyReference<T, W>
    ): IsPropertyReference<T, W> {
        return referenceGetter(this.properties)(parent)
    }

    /** To get a top level reference on a model
     * @param propertyDefinitionGetter The fetcher for the property definition to get reference of
     * @return a reference to property
     */
    fun <T: Any, W: IsPropertyDefinitionWrapper<T, *, *>> ref(parent: IsPropertyReference<out Any, IsPropertyDefinition<*>>? = null, propertyDefinitionGetter: P.()-> W): IsPropertyReference<T, W> {
        @Suppress("UNCHECKED_CAST")
        return propertyDefinitionGetter(this.properties).getRef(parent) as IsPropertyReference<T, W>
    }

    override fun validate(dataObject: DO, refGetter: () -> IsPropertyReference<DO, IsPropertyDefinition<DO>>?) {
        createValidationUmbrellaException(refGetter) { addException ->
            definitions.forEach {
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
            map.forEach { (key, value) ->
                val definition = indexToDefinition[key] ?: return@forEach
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

    /** Convert an object to JSON
     * @param writer to write JSON with
     * @param obj to write to JSON
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeJson(obj: DO, writer: JsonWriter, context: CX? = null) {
        writer.writeStartObject()
        for (def in definitions) {
            val name = def.name
            val value = def.getter(obj) ?: continue

            writer.writeFieldName(name)

            def.definition.writeJsonValue(value, writer, context)
        }
        writer.writeEndObject()
    }

    /** Convert a map with values to JSON
     * @param writer to generate JSON with
     * @param map with values to write to JSON
     * @param context (optional)  with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeJson(map: Map<Int, Any>, writer: JsonWriter, context: CX? = null) {
        writer.writeStartObject()
        for ((key, value) in map) {
            val def = indexToDefinition[key] ?: continue
            val name = def.name

            writer.writeFieldName(name)
            def.definition.writeJsonValue(value, writer, context)
        }
        writer.writeEndObject()
    }

    /** Convert to a DataModel from JSON
     * @param reader to read JSON with
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     * @return map with all the values
     */
    fun readJson(reader: JsonReader, context: CX? = null): Map<Int, Any> {
        if (reader.currentToken == JsonToken.START_JSON){
            reader.nextToken()
        }

        if (reader.currentToken != JsonToken.START_OBJECT) {
            throw IllegalJsonOperation("Expected object at start of json")
        }

        val valueMap: MutableMap<Int, Any> = mutableMapOf()
        reader.nextToken()
        walker@ do {
            val token = reader.currentToken
            when (token) {
                JsonToken.FIELD_NAME -> {
                    val definition = getDefinition(reader.lastValue)
                    if (definition == null) {
                        reader.skipUntilNextField()
                        continue@walker
                    } else {
                        reader.nextToken()

                        valueMap.put(
                                definition.index,
                                definition.definition.readJson(reader, context)
                        )
                    }
                }
                else -> break@walker
            }
            reader.nextToken()
        } while (token !is JsonToken.STOPPED)

        return valueMap
    }

    /** Convert to a DataModel from JSON
     * @param reader to read JSON with
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     * @return DataObject represented by the JSON
     */
    fun readJsonToObject(reader: JsonReader, context: CX? = null) = this(this.readJson(reader, context))

    /** Calculates the byte length for the DataObject contained in map
     * @param map with values to calculate byte length for
     * @param cacher to cache lengths and contexts
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     * @return total bytesize of object
     */
    fun calculateProtoBufLength(map: Map<Int, Any>, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for ((key, value) in map) {
            val def = indexToDefinition[key] ?: continue
            totalByteLength += def.definition.calculateTransportByteLengthWithKey(def.index, value, cacher, context)
        }
        return totalByteLength
    }

    /** Calculates the byte length for the DataObject
     * @param obj to calculate byte length for
     * @param cacher to cache lengths and contexts
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     * @return total bytesize of object
     */
    fun calculateProtoBufLength(obj: DO, cacher: WriteCacheWriter, context: CX? = null) : Int {
        var totalByteLength = 0
        for (def in definitions) {
            val value = def.getter(obj) ?: continue
            totalByteLength += def.definition.calculateTransportByteLengthWithKey(def.index, value, cacher, context)
        }
        return totalByteLength
    }

    /** Write a protobuf from a map with values
     * @param map to write
     * @param cacheGetter to get next cached length or context
     * @param writer to write bytes with
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeProtoBuf(map: Map<Int, Any>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for ((key, value) in map) {
            val def = indexToDefinition[key] ?: continue
            def.definition.writeTransportBytesWithKey(def.index, value, cacheGetter, writer, context)
        }
    }

    /** Write a protobuf from a DataObject
     * @param obj DataObject to write
     * @param cacheGetter to get next length or context
     * @param writer to write bytes with
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeProtoBuf(obj: DO, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for (def in definitions) {
            val value = def.getter(obj) ?: continue
            def.definition.writeTransportBytesWithKey(def.index, value, cacheGetter, writer, context)
        }
    }

    /** Convert to a Map of values from ProtoBuf
     * @param length of bytes to read.
     * @param reader to read ProtoBuf bytes from
     * @param context for contextual parameters in dynamic properties
     * @return DataObject represented by the ProtoBuf
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

    /** Convert to a DataModel from ProtoBuf
     * @param context for contextual parameters in dynamic properties
     * @param length of bytes which contains object
     * @param reader to read ProtoBuf bytes from
     * @param context with context parameters for conversion (for dynamically dependent properties)
     * @return DataObject represented by the ProtoBuf
     */
    fun readProtoBufToObject(length: Int, reader: () -> Byte, context: CX? = null) = this(this.readProtoBuf(length, reader, context))

    /** Read a single field
     * @param valueMap to write the read values to
     * @param key to read for
     * @param byteReader to read bytes for values from
     * @param context with context parameters for conversion (for dynamically dependent properties)
     */
    private fun readProtoBufField(valueMap: MutableMap<Int, Any>, key: ProtoBufKey, byteReader: () -> Byte, context: CX?) {
        val dataObjectPropertyDefinition = indexToDefinition[key.tag]
        val propertyDefinition = dataObjectPropertyDefinition?.definition

        if (propertyDefinition == null) {
            ProtoBuf.skipField(key.wireType, byteReader)
        } else {
            when (propertyDefinition) {
                is IsByteTransportableValue<*, CX> -> valueMap.put(
                        key.tag,
                        propertyDefinition.readTransportBytes(
                                ProtoBuf.getLength(key.wireType, byteReader),
                                byteReader,
                                context
                        )
                )
                is IsByteTransportableCollection<*, *, CX> -> {
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
                is IsByteTransportableMap<*, *, CX> -> {
                    ProtoBuf.getLength(key.wireType, byteReader)
                    val value = propertyDefinition.readMapTransportBytes(
                            byteReader,
                            context
                    )
                    if (valueMap.contains(key.tag)) {
                        @Suppress("UNCHECKED_CAST")
                        val map = valueMap[key.tag] as MutableMap<Any, Any>
                        map.put(value.first, value.second)
                    } else {
                        valueMap[key.tag] = mutableMapOf(value)
                    }
                }
                else -> throw ParseException("Unknown property type for ${dataObjectPropertyDefinition.name}")
            }
        }
    }

    /** Transform context into context specific to DataModel. Override for specific implementation */
    @Suppress("UNCHECKED_CAST")
    open fun transformContext(context: CXI?): CX?  = context as CX?
}
