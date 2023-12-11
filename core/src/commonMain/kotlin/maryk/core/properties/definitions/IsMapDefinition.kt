package maryk.core.properties.definitions

import maryk.core.definitions.MarykPrimitive
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyValueReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapValueReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException

/** Interface for a Map definition with key [K], value [V] and context [CX] */
interface IsMapDefinition<K : Any, V : Any, CX : IsPropertyContext> :
    IsSubDefinition<Map<K, V>, CX>,
    HasSizeDefinition {
    val keyDefinition: IsSimpleValueDefinition<K, CX>
    val valueDefinition: IsSubDefinition<V, CX>

    /** Get a reference to a specific map [key] on [parentMap] */
    fun keyRef(key: K, parentMap: CanContainMapItemReference<*, *, *>?) =
        MapKeyReference(key, this, parentMap)

    /** Get a reference to a specific map value on [parentMap] by [key] */
    fun valueRef(key: K, parentMap: CanContainMapItemReference<*, *, *>?) =
        MapValueReference(key, this, parentMap)

    /** Get a reference to any map value on [parentMap] */
    fun anyValueRef(parentMap: CanContainMapItemReference<*, *, *>?) =
        MapAnyValueReference(this, parentMap)

    override fun getEmbeddedByName(name: String): IsDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsDefinitionWrapper<*, *, *, *>? = null

    override fun validateWithRef(
        previousValue: Map<K, V>?,
        newValue: Map<K, V>?,
        refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K, V>>, *>?
    ) {
        super.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null) {
            val mapSize = newValue.size.toUInt()
            validateSize(mapSize, refGetter)

            createValidationUmbrellaException(refGetter) { addException ->
                for ((key, value) in newValue) {
                    try {
                        this.keyDefinition.validateWithRef(null, key) {
                            this.keyRef(key, refGetter() as CanContainMapItemReference<*, *, *>?)
                        }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                    try {
                        this.valueDefinition.validateWithRef(null, value) {
                            this.valueRef(key, refGetter() as CanContainMapItemReference<*, *, *>?)
                        }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    /** Validates [mapSize] and throws exception pointing to reference of [refGetter] if it fails */
    fun validateSize(
        mapSize: UInt,
        refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K, V>>, *>?
    ) {
        if (isSizeToSmall(mapSize)) {
            throw NotEnoughItemsException(refGetter(), mapSize, this.minSize!!)
        }
        if (isSizeToBig(mapSize)) {
            throw TooManyItemsException(refGetter(), mapSize, this.maxSize!!)
        }
    }

    override fun writeJsonValue(value: Map<K, V>, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeStartObject()
        value.forEach { (k, v) ->
            writer.writeFieldName(
                keyDefinition.asString(k, context)
            )
            valueDefinition.writeJsonValue(v, writer, context)
        }
        writer.writeEndObject()
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): Map<K, V> {
        if (reader.currentToken !is StartObject) {
            throw ParseException("JSON value should be an Object")
        }
        val map: MutableMap<K, V> = mutableMapOf()

        while (reader.nextToken() !== EndObject) {
            reader.currentToken.apply {
                if (this is FieldName) {
                    val key = this.value?.let {
                        keyDefinition.fromString(it, context)
                    } ?: throw ParseException("Map key cannot be null")

                    reader.nextToken()
                    map[key] = valueDefinition.readJson(reader, context)
                } else {
                    throw ParseException("JSON value should be an Object Field but was ${this.name}")
                }
            }
        }
        return map
    }

    override fun calculateTransportByteLengthWithKey(
        index: Int,
        value: Map<K, V>,
        cacher: WriteCacheWriter,
        context: CX?
    ): Int {
        var totalByteLength = 0
        for ((key, item) in value) {
            totalByteLength += ProtoBuf.calculateKeyLength(index.toUInt())

            // Cache length for length delimiter
            val container = ByteLengthContainer()
            cacher.addLengthToCache(container)

            var fieldLength = 0
            fieldLength += keyDefinition.calculateTransportByteLengthWithKey(1, key, cacher, context)
            fieldLength += valueDefinition.calculateTransportByteLengthWithKey(2, item, cacher, context)
            container.length = fieldLength // set length for value
            fieldLength += fieldLength.calculateVarByteLength() // Add field length for length delimiter

            totalByteLength += fieldLength
        }
        return totalByteLength
    }

    override fun writeTransportBytesWithKey(
        index: Int,
        value: Map<K, V>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        for ((key, item) in value) {
            ProtoBuf.writeKey(index.toUInt(), LENGTH_DELIMITED, writer)
            cacheGetter.nextLengthFromCache().writeVarBytes(writer)
            keyDefinition.writeTransportBytesWithKey(1, key, cacheGetter, writer, context)
            valueDefinition.writeTransportBytesWithKey(2, item, cacheGetter, writer, context)
        }
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Map<K, V>?
    ): Map<K, V> {
        var lengthToGo = length
        val countingReader = {
            lengthToGo--
            reader()
        }

        val map = earlierValue as MutableMap<K, V>? ?: mutableMapOf()

        val keyOfMapKey = ProtoBuf.readKey(countingReader)
        val key = keyDefinition.readTransportBytes(
            ProtoBuf.getLength(keyOfMapKey.wireType, countingReader),
            countingReader,
            context
        )

        // Read values until length is exhausted
        do {
            val keyOfMapValue = ProtoBuf.readKey(countingReader)
            map[key] = valueDefinition.readTransportBytes(
                ProtoBuf.getLength(keyOfMapValue.wireType, countingReader),
                countingReader,
                context,
                map[key]
            )
        } while (lengthToGo > 0)

        return map
    }

    override fun compatibleWith(
        definition: IsPropertyDefinition<*>,
        addIncompatibilityReason: ((String) -> Unit)?
    ): Boolean {
        var compatible = super.compatibleWith(definition, addIncompatibilityReason)

        if (definition is IsMapDefinition<*, *, *>) {
            compatible = isCompatible(definition, addIncompatibilityReason) && compatible

            compatible = keyDefinition.compatibleWith(definition.keyDefinition) { addIncompatibilityReason?.invoke("Key: $it") } && compatible
            compatible = valueDefinition.compatibleWith(definition.valueDefinition) { addIncompatibilityReason?.invoke("Value: $it") } && compatible
        }

        return compatible
    }

    override fun getAllDependencies(dependencySet: MutableList<MarykPrimitive>) {
        keyDefinition.getAllDependencies(dependencySet)
        valueDefinition.getAllDependencies(dependencySet)
    }
}
