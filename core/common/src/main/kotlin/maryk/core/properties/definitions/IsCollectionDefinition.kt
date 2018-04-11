package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.exceptions.TooLittleItemsException
import maryk.core.properties.exceptions.TooMuchItemsException
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.createValidationUmbrellaException
import maryk.core.properties.references.IsPropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter
import maryk.json.IsJsonLikeReader
import maryk.json.IsJsonLikeWriter
import maryk.json.JsonToken
import maryk.lib.exceptions.ParseException

/**
 * Interface to define a Collection [C] containing [T] with context [CX]
 */
interface IsCollectionDefinition<T: Any, C: Collection<T>, in CX: IsPropertyContext, out ST: IsValueDefinition<T, CX>>
    : IsByteTransportableCollection<T, C, CX>, HasSizeDefinition, IsTransportablePropertyDefinitionType {
    val valueDefinition: ST

    override fun getEmbeddedByName(name: String): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun getEmbeddedByIndex(index: Int): IsPropertyDefinitionWrapper<*, *, *>? = null

    override fun validateWithRef(previousValue: C?, newValue: C?, refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>>?) {
        super.validateWithRef(previousValue, newValue, refGetter)

        if (newValue != null) {
            val size = newValue.size
            if (isSizeToSmall(size)) {
                throw TooLittleItemsException(refGetter(), size, this.minSize!!)
            }
            if (isSizeToBig(size)) {
                throw TooMuchItemsException(refGetter(), size, this.maxSize!!)
            }

            createValidationUmbrellaException(refGetter) { addException ->
                validateCollectionForExceptions(refGetter, newValue) { item, itemRefFactory ->
                    try {
                        this.valueDefinition.validateWithRef(null, item, { itemRefFactory() })
                    } catch (e: ValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    /** Validates the collection [newValue] with [validator] or get reference from [refGetter] for exception */
    fun validateCollectionForExceptions(
        refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>>?,
        newValue: C,
        validator: (item: T, itemRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any
    )

    /** Creates a new mutable instance of the collection within optional [context] */
    override fun newMutableCollection(context: CX?): MutableCollection<T>

    /** Write [value] to JSON [writer] with [context] */
    override fun writeJsonValue(value: C, writer: IsJsonLikeWriter, context: CX?) {
        val renderCompact = valueDefinition is IsSimpleValueDefinition<*, *>
                && value.size < 5

        writer.writeStartArray(renderCompact)
        for (it in value) {
            valueDefinition.writeJsonValue(it, writer, context)
        }
        writer.writeEndArray()
    }

    /** Read Collection from JSON [reader] within optional [context] */
    override fun readJson(reader: IsJsonLikeReader, context: CX?): C {
        if (reader.currentToken !is JsonToken.StartArray) {
            throw ParseException("JSON value should be an Array")
        }
        val collection: MutableCollection<T> = newMutableCollection(context)

        while (reader.nextToken() !== JsonToken.EndArray) {
            collection.add(
                valueDefinition.readJson(reader, context)
            )
        }
        @Suppress("UNCHECKED_CAST")
        return collection as C
    }

    override fun calculateTransportByteLengthWithKey(index: Int, value: C, cacher: WriteCacheWriter, context: CX?): Int {
        var totalByteSize = 0
        when(this.valueDefinition.wireType) {
            WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> {
                // Cache length for length delimiter
                val container = ByteLengthContainer()
                cacher.addLengthToCache(container)

                value.forEach { item ->
                    totalByteSize += valueDefinition.calculateTransportByteLength(item, cacher, context)
                }
                container.length = totalByteSize

                totalByteSize += ProtoBuf.calculateKeyLength(index)
                totalByteSize += container.length.calculateVarByteLength()
            }
            else -> for (item in value) {
                totalByteSize += valueDefinition.calculateTransportByteLengthWithKey(index, item, cacher, context)
            }
        }

        return totalByteSize
    }

    override fun writeTransportBytesWithKey(index: Int, value: C, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        when(this.valueDefinition.wireType) {
            WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> {
                ProtoBuf.writeKey(index, WireType.LENGTH_DELIMITED, writer)
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

    override fun isPacked(context: CX?, encodedWireType: WireType) = when(this.valueDefinition.wireType) {
        WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> encodedWireType == WireType.LENGTH_DELIMITED
        else -> false
    }

    override fun readCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?) =
        valueDefinition.readTransportBytes(length, reader, context)

    override fun readPackedCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?): C {
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
