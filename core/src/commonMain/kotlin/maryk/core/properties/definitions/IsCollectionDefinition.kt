package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.NotEnoughItemsException
import maryk.core.properties.exceptions.TooManyItemsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType.BIT_32
import maryk.core.protobuf.WireType.BIT_64
import maryk.core.protobuf.WireType.LENGTH_DELIMITED
import maryk.core.protobuf.WireType.VAR_INT
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.core.query.RequestContext
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken.EndArray
import maryk.json.JsonToken.NullValue
import maryk.json.JsonToken.StartArray
import maryk.json.JsonToken.Stopped
import maryk.lib.exceptions.ParseException

/**
 * Interface to define a Collection [C] containing [T] with context [CX]
 */
interface IsCollectionDefinition<T : Any, C : Collection<T>, in CX : IsPropertyContext, out ST : IsValueDefinition<T, CX>> :
    IsSubDefinition<C, CX>,
    HasSizeDefinition {
    val valueDefinition: ST

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *, *>? = null
    override fun getEmbeddedByIndex(index: UInt): IsPropertyDefinitionWrapper<*, *, *, *>? = null

    override fun validateWithRef(
        previousValue: C?,
        newValue: C?,
        refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>, *>?
    ) {
        super.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null) {
            validateSize(newValue.size.toUInt(), refGetter)

            createValidationUmbrellaException(refGetter) { addException ->
                validateCollectionForExceptions(refGetter, newValue) { item, itemRefFactory ->
                    try {
                        this.valueDefinition.validateWithRef(null, item) { itemRefFactory() }
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    /** Validates the [newSize] of the collection and throws exception with reference generated by [refGetter] */
    fun validateSize(
        newSize: UInt,
        refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>, *>?
    ) {
        if (isSizeToSmall(newSize)) {
            throw NotEnoughItemsException(refGetter(), newSize, this.minSize!!)
        }
        if (isSizeToBig(newSize)) {
            throw TooManyItemsException(refGetter(), newSize, this.maxSize!!)
        }
    }

    /** Validates the collection [newValue] with [validator] or get reference from [refGetter] for exception */
    fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>, *>?,
        newValue: C,
        validator: (item: T, itemRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>, *>?) -> Any
    )

    /**
     * Creates a new mutable collection of type T
     * Pass a [context] to read more complex properties which depend on other properties
     */
    fun newMutableCollection(context: CX?): MutableCollection<T>

    /** Write [value] to JSON [writer] with [context] */
    override fun writeJsonValue(value: C, writer: IsJsonLikeWriter, context: CX?) {
        val renderCompact = this.valueDefinition !is EmbeddedValuesDefinition<*, *>
                && this.valueDefinition !is IsEmbeddedObjectDefinition<*, *, *, *, *>
                && this.valueDefinition !is ValueModelDefinition<*, *, *>
                && this.valueDefinition !is ContextualEmbeddedObjectDefinition<*>
                && this.valueDefinition !is ContextualEmbeddedValuesDefinition<*>
                && this.valueDefinition !is MultiTypeDefinition<*, *, *>
                && value.size < 5
        writer.writeStartArray(renderCompact)
        for (it in value) {
            this.valueDefinition.writeJsonValue(it, writer, context)
        }
        writer.writeEndArray()
    }

    /** Read Collection from JSON [reader] within optional [context] */
    override fun readJson(reader: IsJsonLikeReader, context: CX?): C {
        if (reader.currentToken == NullValue) {
            @Suppress("UNCHECKED_CAST")
            return newMutableCollection(context) as C
        }

        if (reader.currentToken !is StartArray) {
            throw ParseException("JSON value should be an Array")
        }
        val collection: MutableCollection<T> = newMutableCollection(context)

        while (reader.nextToken() !== EndArray && reader.currentToken !is Stopped) {
            collection.add(
                valueDefinition.readJson(reader, context)
            )
        }
        @Suppress("UNCHECKED_CAST")
        return collection as C
    }

    override fun calculateTransportByteLengthWithKey(
        index: UInt,
        value: C,
        cacher: WriteCacheWriter,
        context: CX?
    ): Int {
        var totalByteSize = 0
        when (this.valueDefinition.wireType) {
            BIT_64, BIT_32, VAR_INT -> {
                // Cache length for length delimiter
                val container = ByteLengthContainer()
                cacher.addLengthToCache(container)

                value.forEachIndexed { position, item ->
                    if (context is RequestContext) {
                        context.collectInjectLevel(this, this.getItemPropertyRefCreator(position.toUInt(), item))
                    }

                    totalByteSize += valueDefinition.calculateTransportByteLength(item, cacher, context)
                }
                container.length = totalByteSize

                totalByteSize += ProtoBuf.calculateKeyLength(index)
                totalByteSize += container.length.calculateVarByteLength()
            }
            else -> value.forEachIndexed { position, item ->
                if (context is RequestContext) {
                    context.collectInjectLevel(this, this.getItemPropertyRefCreator(position.toUInt(), item))
                }

                totalByteSize += valueDefinition.calculateTransportByteLengthWithKey(index, item, cacher, context)
            }
        }

        if (context is RequestContext && value.isNotEmpty()) {
            context.closeInjectLevel(this)
        }

        return totalByteSize
    }

    override fun writeTransportBytesWithKey(
        index: UInt,
        value: C,
        cacheGetter: WriteCacheReader,
        writer: (byte: Byte) -> Unit,
        context: CX?
    ) {
        when (this.valueDefinition.wireType) {
            BIT_64, BIT_32, VAR_INT -> {
                ProtoBuf.writeKey(index, LENGTH_DELIMITED, writer)
                cacheGetter.nextLengthFromCache().writeVarBytes(writer)
                value.forEach { item ->
                    valueDefinition.writeTransportBytes(item, cacheGetter, writer, context)
                }
            }
            else -> for (item in value) {
                valueDefinition.writeTransportBytesWithKey(index, item, cacheGetter, writer, context)
            }
        }
    }

    /** Get a property reference creator for collection [item] and [index] */
    fun getItemPropertyRefCreator(index: UInt, item: T): (AnyPropertyReference?) -> IsPropertyReference<Any, *, *> {
        throw NotImplementedError()
    }

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?, earlierValue: C?): C {
        when {
            isPacked(length) -> {
                val collection = this.readPackedCollectionTransportBytes(
                    length,
                    reader,
                    context
                )

                @Suppress("UNCHECKED_CAST")
                return when(earlierValue) {
                    null -> collection
                    else -> (earlierValue as MutableCollection<T>).addAll(collection) as C
                }
            }
            else -> {
                val value = valueDefinition.readTransportBytes(length, reader, context)
                val collection = when(earlierValue) {
                    null -> newMutableCollection(context)
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        (earlierValue as MutableCollection<T>)
                    }
                }
                collection.add(value)

                @Suppress("UNCHECKED_CAST")
                return collection as C
            }
        }
    }

    /** Packed is true when encoded with longer length than expected byte size for single */
    private fun isPacked(length: Int) = when (this.valueDefinition.wireType) {
        BIT_64, BIT_32, VAR_INT -> length > (this.valueDefinition as IsFixedStorageBytesEncodable<*>).byteSize
        else -> false
    }

    /**
     * Reads the packed transport bytes from [reader] until [length] into a collection
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    private fun readPackedCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?): C {
        var byteCounter = 0

        val byteReader = {
            byteCounter++
            reader()
        }

        val collection = this.newMutableCollection(context)

        while (byteCounter < length) {
            collection += valueDefinition.readTransportBytes(length, byteReader, context)
        }

        @Suppress("UNCHECKED_CAST")
        return collection as C
    }
}
