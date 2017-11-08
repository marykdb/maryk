package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.CanHaveSimpleChildReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

class MapDefinition<K: Any, V: Any, CX: IsPropertyContext>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = false,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        val keyDefinition: AbstractSimpleValueDefinition<K, CX>,
        val valueDefinition: AbstractSubDefinition<V, CX>
) : AbstractPropertyDefinition<Map<K, V>>(
        name, index, indexed, searchable, required, final
), HasSizeDefinition, IsSerializablePropertyDefinition<Map<K, V>, CX> {
    init {
        assert(keyDefinition.required, { "Definition for key should be required on map: $name" })
        assert(valueDefinition.required, { "Definition for value should be required on map: $name" })
    }

    override fun getRef(parentRefFactory: () -> PropertyReference<*, *>?): PropertyReference<Map<K, V>, AbstractPropertyDefinition<Map<K, V>>> =
        when (valueDefinition) {
            is SubModelDefinition<*, *> -> CanHaveSimpleChildReference(
                    this,
                    parentRefFactory()?.let {
                        it as CanHaveComplexChildReference<*, *>
                    },
                    dataModel = valueDefinition.dataModel
            )
            else -> { super.getRef(parentRefFactory)}
        }

    override fun validate(previousValue: Map<K,V>?, newValue: Map<K,V>?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)

        if (newValue != null) {
            val mapSize = newValue.size
            if (isSizeToSmall(mapSize)) {
                throw PropertyTooLittleItemsException(this.getRef(parentRefFactory), mapSize, this.minSize!!)
            }
            if (isSizeToBig(mapSize)) {
                throw PropertyTooMuchItemsException(this.getRef(parentRefFactory), mapSize, this.maxSize!!)
            }

            createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
                @Suppress("UNCHECKED_CAST")
                newValue.forEach { key, value ->
                    try {
                        this.keyDefinition.validate(null, key) {
                            MapKeyReference(key, this.getRef(parentRefFactory) as PropertyReference<Map<K, V>, MapDefinition<K, V, CX>>)
                        }
                    } catch (e: PropertyValidationException) {
                        addException(e)
                    }
                    try {
                        this.valueDefinition.validate(null, value) {
                            MapValueReference(key, this.getRef(parentRefFactory) as PropertyReference<Map<K, V>, MapDefinition<K, V, CX>>)
                        }
                    } catch (e: PropertyValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    override fun writeJsonValue(value: Map<K, V>, writer: JsonWriter, context: CX?) {
        writer.writeStartObject()
        value.forEach { k, v ->
            writer.writeFieldName(
                    keyDefinition.asString(k)
            )
            valueDefinition.writeJsonValue(v, writer, context)
        }
        writer.writeEndObject()
    }

    override fun readJson(reader: JsonReader, context: CX?): Map<K, V> {
        if (reader.currentToken !is JsonToken.START_OBJECT) {
            throw ParseException("JSON value for $name should be an Object")
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

    override fun calculateTransportByteLengthWithKey(value: Map<K, V>, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?): Int {
        var totalByteLength = 0
        value.forEach { key, item ->
            totalByteLength += ProtoBuf.calculateKeyLength(this.index)

            // Cache length for length delimiter
            val container = ByteLengthContainer()
            lengthCacher(container)

            var fieldLength = 0
            fieldLength += keyDefinition.calculateTransportByteLengthWithKey(1, key, lengthCacher, context)
            fieldLength += valueDefinition.calculateTransportByteLengthWithKey(2, item, lengthCacher, context)
            fieldLength += fieldLength.calculateVarByteLength() // Add field length for length delimiter
            container.length = fieldLength // set length for value

            totalByteLength += fieldLength
        }
        return totalByteLength
    }

    override fun writeTransportBytesWithKey(value: Map<K, V>, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        value.forEach { key, item ->
            ProtoBuf.writeKey(this.index, WireType.LENGTH_DELIMITED, writer)
            lengthCacheGetter().writeVarBytes(writer)
            keyDefinition.writeTransportBytesWithKey(1, key, lengthCacheGetter, writer, context)
            valueDefinition.writeTransportBytesWithKey(2, item, lengthCacheGetter, writer, context)
        }
    }

    /** Read the transport bytes as a map
     * @param reader to read bytes with for map
     * @param context for contextual parameters in reading dynamic properties
     * @return Pair of key value
     */
    fun readMapTransportBytes(reader: () -> Byte, context: CX? = null): Pair<K, V> {
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