package maryk.core.properties.definitions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.models.ContextualDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextTransformerDefinition
import maryk.core.properties.definitions.contextual.ContextualMapDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.CanContainMapItemReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.TypedValue
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.values.SimpleObjectValues
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndObject
import maryk.json.JsonToken.FieldName
import maryk.json.JsonToken.StartObject
import maryk.lib.exceptions.ParseException

/** Definition for Map property */
data class MapDefinition<K : Any, V : Any, CX : IsPropertyContext> internal constructor(
    override val required: Boolean = true,
    override val final: Boolean = false,
    override val minSize: UInt? = null,
    override val maxSize: UInt? = null,
    override val keyDefinition: IsSimpleValueDefinition<K, CX>,
    override val valueDefinition: IsSubDefinition<V, CX>,
    override val default: Map<K, V>? = null
) :
    HasSizeDefinition,
    IsUsableInMapValue<Map<K, V>, CX>,
    IsUsableInMultiType<Map<K, V>, CX>,
    IsMapDefinition<K, V, CX>,
    IsTransportablePropertyDefinitionType<Map<K, V>>,
    HasDefaultValueDefinition<Map<K, V>> {
    override val propertyDefinitionType = PropertyDefinitionType.Map

    init {
        require(keyDefinition.required) { "Definition for key should be required on map" }
        require(valueDefinition.required) { "Definition for value should be required on map" }
    }

    constructor(
        required: Boolean = true,
        final: Boolean = false,
        minSize: UInt? = null,
        maxSize: UInt? = null,
        keyDefinition: IsSimpleValueDefinition<K, CX>,
        valueDefinition: IsUsableInMapValue<V, CX>,
        default: Map<K, V>? = null
    ) : this(required, final, minSize, maxSize, keyDefinition, valueDefinition as IsSubDefinition<V, CX>, default)

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun getEmbeddedByIndex(index: UInt): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun validateWithRef(
        previousValue: Map<K, V>?,
        newValue: Map<K, V>?,
        refGetter: () -> IsPropertyReference<Map<K, V>, IsPropertyDefinition<Map<K, V>>, *>?
    ) {
        super<IsMapDefinition>.validateWithRef(previousValue, newValue, refGetter)

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

    /** Validates size of map and throws exception if it fails*/
    override fun validateSize(
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
                keyDefinition.asString(k)
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
                        keyDefinition.fromString(it)
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
        index: UInt,
        value: Map<K, V>,
        cacher: WriteCacheWriter,
        context: CX?
    ): Int {
        var totalByteLength = 0
        for ((key, item) in value) {
            totalByteLength += ProtoBuf.calculateKeyLength(index)

            // Cache length for length delimiter
            val container = ByteLengthContainer()
            cacher.addLengthToCache(container)

            var fieldLength = 0
            fieldLength += keyDefinition.calculateTransportByteLengthWithKey(1u, key, cacher, context)
            fieldLength += valueDefinition.calculateTransportByteLengthWithKey(2u, item, cacher, context)
            container.length = fieldLength // set length for value
            fieldLength += fieldLength.calculateVarByteLength() // Add field length for length delimiter

            totalByteLength += fieldLength
        }
        return totalByteLength
    }

    override fun writeTransportBytesWithKey(
        index: UInt,
        value: Map<K, V>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        value.forEach { (key, item) ->
            ProtoBuf.writeKey(index, LENGTH_DELIMITED, writer)
            cacheGetter.nextLengthFromCache().writeVarBytes(writer)
            keyDefinition.writeTransportBytesWithKey(1u, key, cacheGetter, writer, context)
            valueDefinition.writeTransportBytesWithKey(2u, item, cacheGetter, writer, context)
        }
    }

    override fun readTransportBytes(
        length: Int,
        reader: () -> Byte,
        context: CX?,
        earlierValue: Map<K, V>?
    ): Map<K, V> {
        val value = this.readMapTransportBytes(
            reader,
            context
        )

        return if (earlierValue != null) {
            val map = earlierValue as MutableMap<K, V>
            map[value.first] = value.second
            map
        } else {
            mutableMapOf(value)
        }
    }

    /** Read the transport bytes on [reader] with [context] into a map key/value pair */
    private fun readMapTransportBytes(reader: () -> Byte, context: CX? = null): Pair<K, V> {
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

    object Model :
        ContextualDataModel<MapDefinition<*, *, *>, ObjectPropertyDefinitions<MapDefinition<*, *, *>>, ContainsDefinitionsContext, KeyValueDefinitionContext>(
            contextTransformer = { KeyValueDefinitionContext(it) },
            properties = object : ObjectPropertyDefinitions<MapDefinition<*, *, *>>() {
                init {
                    IsPropertyDefinition.addRequired(this, MapDefinition<*, *, *>::required)
                    IsPropertyDefinition.addFinal(this, MapDefinition<*, *, *>::final)
                    HasSizeDefinition.addMinSize(3u, this, MapDefinition<*, *, *>::minSize)
                    HasSizeDefinition.addMaxSize(4u, this, MapDefinition<*, *, *>::maxSize)

                    add(5u, "keyDefinition",
                        ContextTransformerDefinition(
                            contextTransformer = { it?.definitionsContext },
                            definition = MultiTypeDefinition(
                                typeEnum = PropertyDefinitionType,
                                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                            )
                        ),
                        getter = MapDefinition<*, *, *>::keyDefinition,
                        toSerializable = { value, _ ->
                            val defType = value as? IsTransportablePropertyDefinitionType<*>
                                ?: throw RequestException("$value is not transportable")
                            TypedValue(defType.propertyDefinitionType, defType)
                        },
                        fromSerializable = {
                            it?.value as IsSimpleValueDefinition<*, *>?
                        },
                        capturer = { context: KeyValueDefinitionContext, value: TypedValue<PropertyDefinitionType, *> ->
                            @Suppress("UNCHECKED_CAST")
                            context.keyDefinition = value.value as IsSimpleValueDefinition<Any, IsPropertyContext>
                        }
                    )

                    add(6u, "valueDefinition",
                        ContextTransformerDefinition(
                            contextTransformer = { it?.definitionsContext },
                            definition = MultiTypeDefinition(
                                typeEnum = PropertyDefinitionType,
                                definitionMap = mapOfPropertyDefEmbeddedObjectDefinitions
                            )
                        ),
                        getter = MapDefinition<*, *, *>::valueDefinition,
                        toSerializable = { value, _ ->
                            val defType = value as? IsTransportablePropertyDefinitionType<*>
                                ?: throw RequestException("$value is not transportable")
                            TypedValue(defType.propertyDefinitionType, value)
                        },
                        fromSerializable = {
                            it?.value as IsSubDefinition<*, *>?
                        },
                        capturer = { context: KeyValueDefinitionContext, value ->
                            @Suppress("UNCHECKED_CAST")
                            context.valueDefinition = value.value as IsSubDefinition<Any, IsPropertyContext>
                        }
                    )

                    @Suppress("UNCHECKED_CAST")
                    add(
                        7u, "default",
                        ContextualMapDefinition(
                            contextualResolver = { context: KeyValueDefinitionContext? ->
                                context?.mapDefinition ?: throw ContextNotFoundException()
                            },
                            required = false
                        ) as IsContextualEncodable<Map<out Any, Any>, KeyValueDefinitionContext>,
                        MapDefinition<*, *, *>::default
                    )
                }
            }
        ) {
        override fun invoke(values: SimpleObjectValues<MapDefinition<*, *, *>>) = MapDefinition(
            required = values(1u),
            final = values(2u),
            minSize = values(3u),
            maxSize = values(4u),
            keyDefinition = values<IsSimpleValueDefinition<*, *>>(5u),
            valueDefinition = values<IsSubDefinition<*, *>>(6u),
            default = values(7u)
        )
    }
}

class KeyValueDefinitionContext(
    val definitionsContext: ContainsDefinitionsContext?,
    var keyDefinition: IsSimpleValueDefinition<Any, IsPropertyContext>? = null,
    var valueDefinition: IsSubDefinition<Any, IsPropertyContext>? = null
) : IsPropertyContext {

    private var _mapDefinition: Lazy<MapDefinition<Any, Any, IsPropertyContext>> = lazy {
        MapDefinition(
            keyDefinition = this.keyDefinition ?: throw ContextNotFoundException(),
            valueDefinition = this.valueDefinition ?: throw ContextNotFoundException()
        )
    }

    val mapDefinition: MapDefinition<Any, Any, IsPropertyContext> get() = this._mapDefinition.value
}
