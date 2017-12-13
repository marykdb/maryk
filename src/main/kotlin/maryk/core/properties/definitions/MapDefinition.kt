package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.TooLittleItemsException
import maryk.core.properties.exceptions.TooMuchItemsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

data class MapDefinition<K: Any, V: Any, CX: IsPropertyContext>(
        override val indexed: Boolean = false,
        override val searchable: Boolean = true,
        override val required: Boolean = true,
        override val final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        override val keyDefinition: IsSimpleValueDefinition<K, CX>,
        override val valueDefinition: IsSubDefinition<V, CX>
) :
        HasSizeDefinition,
        IsByteTransportableMap<K, V, CX>,
        IsMapDefinition<K, V, CX>,
        IsTransportablePropertyDefinitionType
{
    override val propertyDefinitionType = PropertyDefinitionType.Map

    init {
        require(keyDefinition.required, { "Definition for key should be required on map" })
        require(valueDefinition.required, { "Definition for value should be required on map" })
    }

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    /** Get a reference to a specific map key
     * @param key to get reference for
     * @param parentMap (optional) reference to parent map
     */
    fun getKeyRef(key: K, parentMap: MapReference<K, V, CX>?)
            = MapKeyReference(key, this, parentMap)

    /** Get a reference to a specific map value by key
     * @param key to get reference to value for
     * @param parentMap (optional) reference to parent map
     */
    fun getValueRef(key: K, parentMap: MapReference<K, V, CX>?)
            = MapValueReference(key, this, parentMap)

    override fun validateWithRef(previousValue: Map<K,V>?, newValue: Map<K,V>?, refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K,V>>>?) {
        super<IsByteTransportableMap>.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null) {
            val mapSize = newValue.size
            if (isSizeToSmall(mapSize)) {
                throw TooLittleItemsException(refGetter(), mapSize, this.minSize!!)
            }
            if (isSizeToBig(mapSize)) {
                throw TooMuchItemsException(refGetter(), mapSize, this.maxSize!!)
            }

            createValidationUmbrellaException(refGetter) { addException ->
                newValue.forEach { (key, value) ->
                    try {
                        this.keyDefinition.validateWithRef(null, key, {
                            @Suppress("UNCHECKED_CAST")
                            this.getKeyRef(key, refGetter() as MapReference<K, V, CX>?)
                        })
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                    try {
                        this.valueDefinition.validateWithRef(null, value, {
                            @Suppress("UNCHECKED_CAST")
                            this.getValueRef(key, refGetter() as MapReference<K, V, CX>?)
                        })
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    override fun writeJsonValue(value: Map<K, V>, writer: JsonWriter, context: CX?) {
        writer.writeStartObject()
        value.forEach { (k, v) ->
            writer.writeFieldName(
                    keyDefinition.asString(k)
            )
            valueDefinition.writeJsonValue(v, writer, context)
        }
        writer.writeEndObject()
    }

    override fun readJson(reader: JsonReader, context: CX?): Map<K, V> {
        if (reader.currentToken !is JsonToken.START_OBJECT) {
            throw ParseException("JSON value should be an Object")
        }
        val map: MutableMap<K, V> = mutableMapOf()

        while (reader.nextToken() !is JsonToken.END_OBJECT) {
            val key = keyDefinition.fromString(reader.lastValue)
            reader.nextToken()

            map.put(
                    key,
                    valueDefinition.readJson(reader, context)
            )
        }
        return map
    }

    override fun calculateTransportByteLengthWithKey(index: Int, value: Map<K, V>, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        value.forEach { (key, item) ->
            totalByteLength += ProtoBuf.calculateKeyLength(index)

            // Cache length for length delimiter
            val container = ByteLengthContainer()
            cacher.addLengthToCache(container)

            var fieldLength = 0
            fieldLength += keyDefinition.calculateTransportByteLengthWithKey(1, key, cacher, context)
            fieldLength += valueDefinition.calculateTransportByteLengthWithKey(2, item, cacher, context)
            fieldLength += fieldLength.calculateVarByteLength() // Add field length for length delimiter
            container.length = fieldLength // set length for value

            totalByteLength += fieldLength
        }
        return totalByteLength
    }

    override fun writeTransportBytesWithKey(index: Int, value: Map<K, V>, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        value.forEach { (key, item) ->
            ProtoBuf.writeKey(index, WireType.LENGTH_DELIMITED, writer)
            cacheGetter.nextLengthFromCache().writeVarBytes(writer)
            keyDefinition.writeTransportBytesWithKey(1, key, cacheGetter, writer, context)
            valueDefinition.writeTransportBytesWithKey(2, item, cacheGetter, writer, context)
        }
    }

    /** Read the transport bytes as a map
     * @param reader to read bytes with for map
     * @param context for contextual parameters in reading dynamic properties
     * @return Pair of key value
     */
    override fun readMapTransportBytes(reader: () -> Byte, context: CX?): Pair<K, V> {
        val keyOfMapKey = ProtoBuf.readKey(reader)
        val key = keyDefinition.readTransportBytes(
                ProtoBuf.getLength(keyOfMapKey.wireType, reader),
                reader,
                context
        )

        val keyOfMapValue = ProtoBuf.readKey(reader)
        val value = valueDefinition.readTransportBytes(
                ProtoBuf.getLength(keyOfMapValue.wireType, reader),
                reader,
                context
        )

        return Pair(key, value)
    }
}