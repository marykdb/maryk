package maryk.core.models.serializers

import maryk.core.inject.Inject
import maryk.core.inject.InjectWithReference
import maryk.core.models.values
import maryk.core.properties.IsObjectPropertyDefinitions
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsTypedPropertyDefinitions
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.IsCollectionDefinition
import maryk.core.properties.definitions.IsEmbeddedObjectDefinition
import maryk.core.properties.definitions.IsMapDefinition
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.values
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.ProtoBufKey
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.core.values.IsValueItems
import maryk.core.values.IsValues
import maryk.core.values.MutableValueItems
import maryk.lib.exceptions.ParseException

/** Serializer for DataModels */
open class DataModelSerializer<DO: Any, V: IsValues<DM>, DM: IsTypedPropertyDefinitions<DO>, CX: IsPropertyContext>(
    val model: DM,
): IsDataModelSerializer<V, DM, CX> {
    override fun calculateProtoBufLength(values: V, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteLength = 0
        for (definition in this.model) {
            val originalValue = values.original(definition.index)

            totalByteLength += this.protoBufLengthToAddForField(originalValue, definition, cacher, context)
        }

        if (context is RequestContext) {
            context.closeInjectLevel(this.model)
        }

        return totalByteLength
    }

    /** Calculates length for the ProtoBuf field for [value] */
    protected open fun protoBufLengthToAddForField(
        value: Any?,
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        cacher: WriteCacheWriter,
        context: CX?
    ): Int {
        if (value == null) {
            return 0 // Skip null value in counting
        }

        // If it is an Inject it needs to be collected for protobuf since it cannot be encoded inline
        // Except if it is the InjectWithReference object in which it is encoded
        if (value is Inject<*, *> && this.model != InjectWithReference) {
            if (context is RequestContext) {
                context.collectInjectLevel(this.model, definition::ref)
                context.collectInject(value)
            }

            return 0 // Don't count length of Inject values since they are encoded in Requests object
        } else if (
            context is RequestContext
            && (definition is IsEmbeddedObjectDefinition<*, *, *, *>
                    || definition is IsCollectionDefinition<*, *, *, *>
                    || definition is IsMapDefinition<*, *, *>)
        ) {
            // Collect inject level if value can contain sub values
            context.collectInjectLevel(this.model, definition::ref)
        }

        definition.capture(context, value)
        return definition.definition.calculateTransportByteLengthWithKey(definition.index.toInt(), value, cacher, context)
    }

    override fun writeProtoBuf(values: V, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        for (definition in this.model) {
            val originalValue = values.original(definition.index)

            this.writeProtoBufField(originalValue, definition, cacheGetter, writer, context)
        }
    }

    /**
     * Writes a specific ProtoBuf field defined by [definition] with [value] to [writer] and get
     * possible cached values from [cacheGetter]
     * Optionally pass a [context] to write more complex properties which depend on other properties
     */
    internal open fun writeProtoBufField(
        value: Any?,
        definition: IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        if (value == null) return // Skip empty values

        if (value is Inject<*, *> && this.model != InjectWithReference) {
            return // Skip Inject values since they are encoded in Requests object
        }

        definition.capture(context, value)

        definition.definition.writeTransportBytesWithKey(definition.index.toInt(), value, cacheGetter, writer, context)
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map of values
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    override fun readProtoBuf(length: Int, reader: () -> Byte, context: CX?): V =
        createValues(context, readProtoBufToMap(length, reader, context))

    /** Creates Values [V] from a [items] map */
    open fun createValues(context: CX?, items: IsValueItems): V {
        @Suppress("UNCHECKED_CAST")
        return when (this.model) {
            is IsObjectPropertyDefinitions<*> ->
                (this.model as IsObjectPropertyDefinitions<Any>).values(context as? RequestContext) {
                    items
                } as V
            is IsValuesPropertyDefinitions ->
                this.model.values(context as? RequestContext) {
                    items
                } as V
            else -> throw Exception("Unknown properties type ${this.model::class.simpleName}")
        }
    }

    /**
     * Read ProtoBuf bytes from [reader] until [length] to a Map
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    internal fun readProtoBufToMap(length: Int, reader: () -> Byte, context: CX? = null): IsValueItems {
        val valueMap = MutableValueItems()
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
     * Read a single field of [key] from [byteReader] into [values]
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    private fun readProtoBufField(values: MutableValueItems, key: ProtoBufKey, byteReader: () -> Byte, context: CX?) {
        val dataObjectPropertyDefinition = model[key.tag]
        val propertyDefinition = dataObjectPropertyDefinition?.definition

        if (propertyDefinition == null) {
            ProtoBuf.skipField(key.wireType, byteReader)
        } else {
            values[key.tag] =
                when (propertyDefinition) {
                    is IsEmbeddedObjectDefinition<Any, *, CX, *> ->
                        propertyDefinition.readTransportBytesToValues(
                            ProtoBuf.getLength(key.wireType, byteReader),
                            byteReader,
                            context
                        )
                    else ->
                        propertyDefinition.readTransportBytes(
                            ProtoBuf.getLength(key.wireType, byteReader),
                            byteReader,
                            context,
                            values[key.tag]
                        )
                }.also {
                    dataObjectPropertyDefinition.capture(context, it)
                }
        }
    }

    /**
     * Utility method to check and map a value to a constructor property
     */
    protected inline fun <reified T, reified TI> Map<UInt, *>.transform(
        index: UInt,
        transform: (TI) -> T,
        default: T? = null
    ): T {
        val value: Any? = this[index]

        if (value !is TI?) {
            val valueDef = model[index]!!
            throw ParseException("Property '${valueDef.name}' with value '$value' should be of type ${(valueDef.definition as IsTransportablePropertyDefinitionType<*>).propertyDefinitionType.name}")
        }

        if (value == null) {
            return when {
                default != null -> default
                value !is TI -> {
                    val valueDef = model[index]!!
                    throw ParseException("Property '${valueDef.name}' with value '$value' cannot be null")
                }
                else -> value
            } as T
        }

        return transform(value as TI)
    }
}
