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
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

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

    /** Validates the collection content */
    fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>>?, newValue: C, validator: (item: T, itemRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any)

    /** Creates a new mutable instance of the collection */
    override fun newMutableCollection(context: CX?): MutableCollection<T>

    override fun writeJsonValue(value: C, writer: JsonWriter, context: CX?) {
        writer.writeStartArray()
        value.forEach {
            valueDefinition.writeJsonValue(it, writer, context)
        }
        writer.writeEndArray()
    }

    override fun readJson(reader: JsonReader, context: CX?): C {
        if (reader.currentToken !is JsonToken.StartArray) {
            throw ParseException("JSON value should be an Array")
        }
        val collection: MutableCollection<T> = newMutableCollection(context)

        while (reader.nextToken() !is JsonToken.EndArray) {
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
            else -> value.forEach { item ->
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
            else -> value.forEach { item ->
                valueDefinition.writeTransportBytesWithKey(index, item, cacheGetter, writer, context)
            }
        }
    }

    override fun isPacked(context: CX?, encodedWireType: WireType) = when(this.valueDefinition.wireType) {
        WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> encodedWireType == WireType.LENGTH_DELIMITED
        else -> false
    }

    override fun readCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX?)
            = valueDefinition.readTransportBytes(length, reader, context)

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