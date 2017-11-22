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
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.ProtoBufKey

class Def<T: Any, in DM: Any, in CX: IsPropertyContext>(val propertyDefinition: IsSerializablePropertyDefinition<T, CX>, val propertyGetter: (DM) -> T?)

/**
 * A Data Model for converting and validating DataObjects
 * @param <DO> Type of DataObject which is modeled
 *
 * @param construct: Constructs object out of a map with values keyed on index.
 * @param definitions: All definitions for properties contained in this model
 * @param DO: Type of DataModel contained
 * @param CX: Type of context object
 */
open class DataModel<DO: Any, in CX: IsPropertyContext>(
        val construct: (Map<Int, *>) -> DO,
        val definitions: List<Def<*, DO, CX>>
) : IsDataModel<DO> {
    private val indexToDefinition: Map<Int, Def<*, DO, CX>>
    private val nameToDefinition: Map<String, Def<*, DO, CX>>

    init {
        indexToDefinition = mutableMapOf()
        nameToDefinition = mutableMapOf()

        definitions.forEach {
            val def = it.propertyDefinition
            assert(def.index in (0..Short.MAX_VALUE), { "${def.index} for ${def.name} is outside range $(0..Short.MAX_VALUE)" })
            assert(indexToDefinition[def.index] == null, { "Duplicate index ${def.index} for ${def.name} and ${indexToDefinition[def.index]?.propertyDefinition?.name}" })
            assert(def.name != null, {"Name of property should be set"})
            indexToDefinition[def.index] = it
            nameToDefinition[def.name!!] = it
        }
    }

    override fun getDefinition(name: String) = nameToDefinition[name]?.propertyDefinition
    override fun getDefinition(index: Int) = indexToDefinition[index]?.propertyDefinition

    override fun getPropertyGetter(name: String) = nameToDefinition[name]?.propertyGetter
    override fun getPropertyGetter(index: Int) = indexToDefinition[index]?.propertyGetter

    @Throws(PropertyValidationUmbrellaException::class)
    override fun validate(dataObject: DO, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
            definitions.forEach {
                @Suppress("UNCHECKED_CAST")
                val def: IsPropertyDefinition<Any> = it.propertyDefinition as IsPropertyDefinition<Any>
                try {
                    def.validate(
                            newValue = it.propertyGetter(dataObject),
                            parentRefFactory = parentRefFactory
                    )
                } catch (e: PropertyValidationException) {
                    addException(e)
                }
            }
        }
    }

    @Throws(PropertyValidationUmbrellaException::class)
    override fun validate(map: Map<Int, Any>, parentRefFactory: () -> IsPropertyReference<*, *>?) {
        createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
            map.forEach { (key, value) ->
                val definition = indexToDefinition[key] ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val def = definition.propertyDefinition as IsPropertyDefinition<Any>
                try {
                    def.validate(
                            newValue = value,
                            parentRefFactory = parentRefFactory
                    )
                } catch (e: PropertyValidationException) {
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
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO, CX>>) {
            val name = def.propertyDefinition.name!!
            val value = def.propertyGetter(obj) ?: continue

            writer.writeFieldName(name)

            def.propertyDefinition.writeJsonValue(value, writer, context)
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
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO, CX>? ?: continue
            val name = def.propertyDefinition.name!!

            writer.writeFieldName(name)
            def.propertyDefinition.writeJsonValue(value, writer, context)
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
                                definition.readJson(reader, context)
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
    fun readJsonToObject(reader: JsonReader, context: CX? = null) = construct(this.readJson(reader, context))

    /** Calculates the byte length for the DataObject contained in map
     * @param map with values to calculate byte length for
     * @param lengthCacher to cache byte lengths
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     * @return total bytesize of object
     */
    fun calculateProtoBufLength(map: Map<Int, Any>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX? = null) : Int {
        var totalByteLength = 0
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO, CX>? ?: continue
            totalByteLength += def.propertyDefinition.calculateTransportByteLengthWithKey(value, lengthCacher, context)
        }
        return totalByteLength
    }

    /** Calculates the byte length for the DataObject
     * @param obj to calculate byte length for
     * @param lengthCacher to cache byte lengths
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     * @return total bytesize of object
     */
    fun calculateProtoBufLength(obj: DO, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX? = null) : Int {
        var totalByteLength = 0
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO, CX>>) {
            val value = def.propertyGetter(obj) ?: continue
            totalByteLength += def.propertyDefinition.calculateTransportByteLengthWithKey(value, lengthCacher, context)
        }
        return totalByteLength
    }

    /** Write a protobuf from a map with values
     * @param map to write
     * @param lengthCacheGetter to get next length
     * @param writer to write bytes with
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeProtoBuf(map: Map<Int, Any>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX? = null) {
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO, CX>? ?: continue
            def.propertyDefinition.writeTransportBytesWithKey(value, lengthCacheGetter, writer, context)
        }
    }

    /** Write a protobuf from a DataObject
     * @param obj DataObject to write
     * @param lengthCacheGetter to get next length
     * @param writer to write bytes with
     * @param context (optional) with context parameters for conversion (for dynamically dependent properties)
     */
    fun writeProtoBuf(obj: DO, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX? = null) {
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO, CX>>) {
            val value = def.propertyGetter(obj) ?: continue
            def.propertyDefinition.writeTransportBytesWithKey(value, lengthCacheGetter, writer, context)
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
    fun readProtoBufToObject(length: Int, reader: () -> Byte, context: CX? = null) = construct(this.readProtoBuf(length, reader, context))

    /** Read a single field
     * @param valueMap to write the read values to
     * @param key to read for
     * @param byteReader to read bytes for values from
     * @param context with context parameters for conversion (for dynamically dependent properties)
     */
    private fun readProtoBufField(valueMap: MutableMap<Int, Any>, key: ProtoBufKey, byteReader: () -> Byte, context: CX?) {
        val propertyDefinition = indexToDefinition[key.tag]?.propertyDefinition

        // CANNOT READ LIST VALUE
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
                else -> throw ParseException("Unknown property type for ${propertyDefinition.name}")
            }
        }
    }
}
