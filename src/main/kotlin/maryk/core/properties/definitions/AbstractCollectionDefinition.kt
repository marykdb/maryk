package maryk.core.properties.definitions

import maryk.core.assert
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

abstract class AbstractCollectionDefinition<
        T: Any, C: Collection<T>,
        in CX: IsPropertyContext,
        out ST: AbstractValueDefinition<T, CX>
>(
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean = false,
        override val minSize: Int?,
        override val maxSize: Int?,
        val valueDefinition: ST
) : AbstractPropertyDefinition<C>(
        indexed, searchable, required, final
), HasSizeDefinition, IsCollectionDefinition<T, C, CX>, IsSerializablePropertyDefinition<C, CX> {
    init {
        assert(valueDefinition.required, { "Definition should have required=true on collection" })
    }

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
    abstract internal fun validateCollectionForExceptions(refGetter: () -> IsPropertyReference<C, IsPropertyDefinition<C>>?, newValue: C, validator: (item: T, itemRefFactory: () -> IsPropertyReference<T, IsPropertyDefinition<T>>?) -> Any)

    /** Creates a new mutable instance of the collection */
    abstract override fun newMutableCollection(context: CX?): MutableCollection<T>

    override fun writeJsonValue(value: C, writer: JsonWriter, context: CX?) {
        writer.writeStartArray()
        value.forEach {
            valueDefinition.writeJsonValue(it, writer, context)
        }
        writer.writeEndArray()
    }

    override fun readJson(reader: JsonReader, context: CX?): C {
        if (reader.currentToken !is JsonToken.START_ARRAY) {
            throw ParseException("JSON value should be an Array")
        }
        val collection: MutableCollection<T> = newMutableCollection(context)

        while (reader.nextToken() !is JsonToken.END_ARRAY) {
            collection.add(
                    valueDefinition.readJson(reader, context)
            )
        }
        @Suppress("UNCHECKED_CAST")
        return collection as C
    }

    override fun calculateTransportByteLengthWithKey(index: Int, value: C, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?): Int {
        var totalByteSize = 0
        when(this.valueDefinition.wireType) {
            WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> {
                // Cache length for length delimiter
                val container = ByteLengthContainer()
                lengthCacher(container)

                value.forEach { item ->
                    totalByteSize += valueDefinition.calculateTransportByteLength(item, lengthCacher, context)
                }
                container.length = totalByteSize

                totalByteSize += ProtoBuf.calculateKeyLength(index)
                totalByteSize += container.length.calculateVarByteLength()
            }
            else -> value.forEach { item ->
                totalByteSize += valueDefinition.calculateTransportByteLengthWithKey(index, item, lengthCacher, context)
            }
        }

        return totalByteSize
    }

    override fun writeTransportBytesWithKey(index: Int, value: C, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        when(this.valueDefinition.wireType) {
            WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> {
                ProtoBuf.writeKey(index, WireType.LENGTH_DELIMITED, writer)
                lengthCacheGetter().writeVarBytes(writer)
                value.forEach { item ->
                    valueDefinition.writeTransportBytes(item, lengthCacheGetter, writer, context)
                }
            }
            else -> value.forEach { item ->
                valueDefinition.writeTransportBytesWithKey(index, item, lengthCacheGetter, writer, context)
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