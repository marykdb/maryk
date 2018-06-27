package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.TooMuchItemsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapKeyReference
import maryk.core.properties.references.MapReference
import maryk.core.properties.references.MapValueReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.DataModelContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/** Definition for Map property */
data class MapDefinition<K: Any, V: Any, CX: IsPropertyContext>(
    override val indexed: Boolean = false,
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: Int? = null,
    override val maxSize: Int? = null,
    override val keyDefinition: IsSimpleValueDefinition<K, CX>,
    override val valueDefinition: IsSubDefinition<V, CX>,
    override val default: Map<K, V>? = null
) :
    HasSizeDefinition,
    IsByteTransportableMap<K, V, CX>,
    IsMapDefinition<K, V, CX>,
    IsTransportablePropertyDefinitionType<Map<K, V>>,
    HasDefaultValueDefinition<Map<K, V>>
{
    override val propertyDefinitionType = PropertyDefinitionType.Map

    init {
        require(keyDefinition.required) { "Definition for key should be required on map" }
        require(valueDefinition.required) { "Definition for value should be required on map" }
    }

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    /** Get a reference to a specific map [key] on [parentMap] */
    fun getKeyRef(key: K, parentMap: MapReference<K, V, CX>?) =
        MapKeyReference(key, this, parentMap)

    /** Get a reference to a specific map value on [parentMap] by [key] */
    fun getValueRef(key: K, parentMap: MapReference<K, V, CX>?) =
        MapValueReference(key, this, parentMap)

    override fun validateWithRef(previousValue: Map<K,V>?, newValue: Map<K,V>?, refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K,V>>>?) {
        super<IsByteTransportableMap>.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null) {
            val mapSize = newValue.size
            if (isSizeToSmall(mapSize)) {
                throw NotEnoughItemsException(refGetter(), mapSize, this.minSize!!)
            }
            if (isSizeToBig(mapSize)) {
                throw TooMuchItemsException(refGetter(), mapSize, this.maxSize!!)
            }

            createValidationUmbrellaException(refGetter) { addException ->
                for ((key, value) in newValue) {
                    try {
                        this.keyDefinition.validateWithRef(null, key) {
                            @Suppress("UNCHECKED_CAST")
                            this.getKeyRef(key, refGetter() as MapReference<K, V, CX>?)
                        }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                    try {
                        this.valueDefinition.validateWithRef(null, value) {
                            @Suppress("UNCHECKED_CAST")
                            this.getValueRef(key, refGetter() as MapReference<K, V, CX>?)
                        }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    override fun writeJsonValue(value: Map<K, V>, writer: IsJsonLikeWriter, context: CX?) {
        writer.writeStartObject()
        value.forEach { (k, v) ->
            writer.writeFieldName(
                keyDefinition.asString(k)
            )
            valueDefinition.writeJsonValue(v, writer, context)
        }
        writer.writeEndObject()
    }

    override fun readJson(reader: IsJsonLikeReader, context: CX?): Map<K, V> {
        if (reader.currentToken !is JsonToken.StartObject) {
            throw ParseException("JSON value should be an Object")
        }
        val map: MutableMap<K, V> = mutableMapOf()

        while (reader.nextToken() !== JsonToken.EndObject) {
            reader.currentToken.apply {
                if (this is JsonToken.FieldName) {
                    val key = this.value?.let {
                        keyDefinition.fromString(it)
                    } ?: throw ParseException("Map key cannot be null")

                    reader.nextToken()
                    map[key] = valueDefinition.readJson(reader, context)
                } else { throw ParseException("JSON value should be an Object Field but was ${this.name}")
                }
            }
        }
        return map
    }

    override fun calculateTransportByteLengthWithKey(index: Int, value: Map<K, V>, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        for ((key, item) in value) {
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

    /** Read the transport bytes on [reader] with [context] into a map key/value pair */
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

    object Model : ContextualDataModel<MapDefinition<*, *, *>, PropertyDefinitions<MapDefinition<*, *, *>>, DataModelContext, KeyValueDefinitionContext>(
        contextTransformer = { it: DataModelContext? -> KeyValueDefinitionContext(it) },
        properties = object : PropertyDefinitions<MapDefinition<*, *, *>>() {
            init {
                IsPropertyDefinition.addIndexed(this, MapDefinition<*, *, *>::indexed)
                IsPropertyDefinition.addRequired(this, MapDefinition<*, *, *>::required)
                IsPropertyDefinition.addFinal(this, MapDefinition<*, *, *>::final)
                HasSizeDefinition.addMinSize(3, this, MapDefinition<*, *, *>::minSize)
                HasSizeDefinition.addMaxSize(4, this, MapDefinition<*, *, *>::maxSize)

                add(5, "keyDefinition",
                    ContextTransformerDefinition(
                        contextTransformer = { it?.dataModelContext },
                        definition = MultiTypeDefinition(
                            typeEnum = PropertyDefinitionType,
                            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                        )
                    ),
                    getter = MapDefinition<*, *, *>::keyDefinition,
                    toSerializable = { value, _ ->
                        val defType = value!! as IsTransportablePropertyDefinitionType<*>
                        TypedValue(defType.propertyDefinitionType, value)
                    },
                    fromSerializable = {
                        @Suppress("UNCHECKED_CAST")
                        it?.value as IsSimpleValueDefinition<Any, DataModelContext>?
                    },
                    capturer = { context: KeyValueDefinitionContext, value ->
                        @Suppress("UNCHECKED_CAST")
                        context.keyDefinion = value.value as IsSimpleValueDefinition<Any, DataModelContext>
                    }
                )

                add(6, "valueDefinition",
                    ContextTransformerDefinition(
                        contextTransformer = { it?.dataModelContext },
                        definition = MultiTypeDefinition(
                            typeEnum = PropertyDefinitionType,
                            definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                        )
                    ),
                    getter = MapDefinition<*, *, *>::valueDefinition,
                    toSerializable = { value, _ ->
                    val defType = value!! as IsTransportablePropertyDefinitionType<*>
                        TypedValue(defType.propertyDefinitionType, value)
                    },
                    fromSerializable = {
                        @Suppress("UNCHECKED_CAST")
                        it?.value as IsValueDefinition<Any, DataModelContext>?
                    },
                    capturer = { context: KeyValueDefinitionContext, value ->
                        @Suppress("UNCHECKED_CAST")
                        context.valueDefinion = value.value as IsValueDefinition<Any, DataModelContext>
                    }
                )

                @Suppress("UNCHECKED_CAST")
                add(7, "default",
                    ContextualMapDefinition(
                        contextualResolver = { context: KeyValueDefinitionContext? ->
                            context?.let {
                                it.mapDefinition as IsByteTransportableMap<Any, Any, KeyValueDefinitionContext>
                            } ?: throw ContextNotFoundException()
                        },
                        required = false
                    ) as IsSerializableFlexBytesEncodable<Map<out Any, Any>, KeyValueDefinitionContext>,
                    MapDefinition<*, *, *>::default
                )
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = MapDefinition(
            indexed = map(0),
            required = map(1),
            final = map(2),
            minSize = map(3),
            maxSize = map(4),
            keyDefinition = map<IsSimpleValueDefinition<*, *>>(5),
            valueDefinition = map<IsValueDefinition<*, *>>(6),
            default = map(7)
        )
    }
}

class KeyValueDefinitionContext(
    val dataModelContext: DataModelContext?
) : IsPropertyContext {
    var keyDefinion: IsSimpleValueDefinition<Any, DataModelContext>? = null
    var valueDefinion: IsValueDefinition<Any, DataModelContext>? = null

    private var _mapDefinition: Lazy<MapDefinition<Any, Any, DataModelContext>> = lazy {
        MapDefinition(
            keyDefinition = this.keyDefinion ?: throw ContextNotFoundException(),
            valueDefinition = this.valueDefinion ?: throw ContextNotFoundException()
        )
    }

    val mapDefinition: MapDefinition<Any, Any, DataModelContext> get() = this._mapDefinition.value
}
