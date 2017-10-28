package maryk.core.objects

import maryk.core.json.IllegalJsonOperation
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.definitions.AbstractSubDefinition
import maryk.core.properties.definitions.IsByteTransportableCollection
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.MapDefinition
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.ProtoBufKey

class Def<T: Any, in DM: Any>(val propertyDefinition: IsPropertyDefinition<T>, val propertyGetter: (DM) -> T?)

/**
 * A Data Model for converting and validating DataObjects
 * @param <DO> Type of DataObject which is modeled
 */
abstract class DataModel<DO: Any>(
        val construct: (Map<Int, *>) -> DO,
        val definitions: List<Def<*, DO>>
) {
    private val indexToDefinition: Map<Int, Def<*, DO>>
    private val nameToDefinition: Map<String, Def<*, DO>>

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

    /** Get the definition with a property name
     * @param name to get property of
     */
    fun getDefinition(name: String) = nameToDefinition[name]?.propertyDefinition

    /** Get the definition with a property index
     * @param index to get property of
     */
    fun getDefinition(index: Int) = indexToDefinition[index]?.propertyDefinition

    /** Get a method to retrieve property from DataObject by name
     * @param name of value to retrieve
     */
    fun getPropertyGetter(name: String) = nameToDefinition[name]?.propertyGetter

    /** Get a method to retrieve property from DataObject by index
     * @param index of value to retrieve
     */
    fun getPropertyGetter(index: Int) = indexToDefinition[index]?.propertyGetter

    /** Validate a DataObject
     * @param dataObject to validate
     * @param parentRefFactory parent reference factory to the model
     * @throws PropertyValidationUmbrellaException if input was invalid
     */
    @Throws(PropertyValidationUmbrellaException::class)
    fun validate(dataObject: DO, parentRefFactory: () -> PropertyReference<*, *>? = { null }) {
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

    /** Validate a map of values
     * @param map with values to validate
     * @param parentRefFactory parent reference factory to the model
     * @throws PropertyValidationUmbrellaException if input was invalid
     */
    @Throws(PropertyValidationUmbrellaException::class)
    fun validate(map: Map<Int, Any>, parentRefFactory: () -> PropertyReference<*, *>? = { null }) {
        createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
            map.forEach { (key, value) ->
                val definition = indexToDefinition[key] ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val def: IsPropertyDefinition<Any> = definition.propertyDefinition as IsPropertyDefinition<Any>
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
     */
    fun writeJson(writer: JsonWriter, obj: DO) {
        writer.writeStartObject()
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO>>) {
            val name = def.propertyDefinition.name!!
            val value = def.propertyGetter(obj) ?: continue

            writer.writeFieldName(name)

            def.propertyDefinition.writeJsonValue(writer, value)
        }
        writer.writeEndObject()
    }

    /** Convert a map with values to JSON
     * @param writer to generate JSON with
     * @param map with values to write to JSON
     */
    fun writeJson(writer: JsonWriter, map: Map<Int, Any>) {
        writer.writeStartObject()
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO>? ?: continue
            val name = def.propertyDefinition.name!!

            writer.writeFieldName(name)
            def.propertyDefinition.writeJsonValue(writer, value)
        }
        writer.writeEndObject()
    }

    /** Convert to a DataModel from JSON
     * @param reader to read JSON with
     * @return map with all the values
     */
    fun readJson(reader: JsonReader): Map<Int, Any> {
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
                                definition.readJson(reader)
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
     * @return DataObject represented by the JSON
     */
    fun readJsonToObject(reader: JsonReader) = construct(this.readJson(reader))

    /** Calculates the byte length for the DataObject contained in map
     * @param map with values to calculate byte length for
     * @param lengthCacher to cache byte lengths
     * @return total bytesize of object
     */
    fun calculateProtoBufLength(map: Map<Int, Any>, lengthCacher: (length: ByteLengthContainer) -> Unit) : Int {
        var totalByteLength = 0
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO>? ?: continue
            totalByteLength += def.propertyDefinition.calculateTransportByteLengthWithKey(value, lengthCacher)
        }
        return totalByteLength
    }

    /** Calculates the byte length for the DataObject
     * @param obj to calculate byte length for
     * @param lengthCacher to cache byte lengths
     * @return total bytesize of object
     */
    fun calculateProtoBufLength(obj: DO, lengthCacher: (length: ByteLengthContainer) -> Unit) : Int {
        var totalByteLength = 0
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO>>) {
            val value = def.propertyGetter(obj) ?: continue
            totalByteLength += def.propertyDefinition.calculateTransportByteLengthWithKey(value, lengthCacher)
        }
        return totalByteLength
    }

    /** Write a protobuf from a map with values
     * @param map to write
     * @param lengthCacheGetter to get next length
     * @param writer to write bytes with
     */
    fun writeProtoBuf(map: Map<Int, Any>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        for ((key, value) in map) {
            @Suppress("UNCHECKED_CAST")
            val def = indexToDefinition[key] as Def<Any, DO>? ?: continue
            def.propertyDefinition.writeTransportBytesWithKey(value, lengthCacheGetter, writer)
        }
    }

    /** Write a protobuf from a DataObject
     * @param map to write
     * @param lengthCacheGetter to get next length
     * @param writer to write bytes with
     */
    fun writeProtoBuf(obj: DO, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        for (def in definitions as List<Def<Any, DO>>) {
            val value = def.propertyGetter(obj) ?: continue
            def.propertyDefinition.writeTransportBytesWithKey(value, lengthCacheGetter, writer)
        }
    }

    /** Convert to a Map of values from ProtoBuf
     * @param length of bytes to read.
     * @param reader to read ProtoBuf bytes from
     * @return DataObject represented by the ProtoBuf
     */
    fun readProtoBuf(length: Int, reader:() -> Byte): Map<Int, Any> {
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
                    byteReader
            )
        }

        return valueMap
    }

    /** Read a single field
     * @param valueMap to write the read values to
     * @param key to read for
     * @param byteReader to read bytes for values from
     */
    private fun readProtoBufField(valueMap: MutableMap<Int, Any>, key: ProtoBufKey, byteReader: () -> Byte) {
        val propertyDefinition = indexToDefinition[key.tag]?.propertyDefinition

        if (propertyDefinition == null) {
            ProtoBuf.skipField(key.wireType, byteReader)
        } else {
            when (propertyDefinition) {
                is AbstractSubDefinition<*> -> valueMap.put(
                        key.tag,
                        propertyDefinition.readTransportBytes(
                                ProtoBuf.getLength(key.wireType, byteReader),
                                byteReader
                        )
                )
                is IsByteTransportableCollection<*, *> -> {
                    when {
                        propertyDefinition.isPacked(key.wireType) -> {
                            @Suppress("UNCHECKED_CAST")
                            val collection = propertyDefinition.readPackedCollectionTransportBytes(
                                    ProtoBuf.getLength(key.wireType, byteReader),
                                    byteReader
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
                                    byteReader
                            )
                            @Suppress("UNCHECKED_CAST")
                            val collection = when {
                                valueMap.contains(key.tag) -> valueMap[key.tag]
                                else -> propertyDefinition.newMutableCollection().let {
                                    valueMap[key.tag] = it
                                    it
                                }
                            } as MutableCollection<Any>
                            collection += value
                        }
                    }
                }
                is MapDefinition<*, *> -> {
                    ProtoBuf.getLength(key.wireType, byteReader)
                    val value = propertyDefinition.readMapTransportBytes(
                            byteReader
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

    /** Convert to a DataModel from ProtoBuf
     * @param length of bytes which contains object
     * @param reader to read ProtoBuf bytes from
     * @return DataObject represented by the ProtoBuf
     */
    fun readProtoBufToObject(length: Int, reader:() -> Byte) = construct(this.readProtoBuf(length, reader))
}
