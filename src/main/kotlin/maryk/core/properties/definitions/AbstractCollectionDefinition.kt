package maryk.core.properties.definitions

import maryk.core.extensions.bytes.calculateVarByteLength
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

abstract class AbstractCollectionDefinition<T: Any, C: Collection<T>>(
        name: String? = null,
        index: Int = -1,
        indexed: Boolean = true,
        searchable: Boolean = true,
        required: Boolean = false,
        final: Boolean = false,
        override val minSize: Int? = null,
        override val maxSize: Int? = null,
        val valueDefinition: AbstractValueDefinition<T>
) : AbstractPropertyDefinition<C>(
        name, index, indexed, searchable, required, final
), HasSizeDefinition, IsByteTransportableCollection<T, C> {
    init {
        assert(valueDefinition.required, { "Definition should have required=true on collection «$name»" })
    }

    override fun validate(previousValue: C?, newValue: C?, parentRefFactory: () -> PropertyReference<*, *>?) {
        super.validate(previousValue, newValue, parentRefFactory)

        if (newValue != null) {
            val size = getSize(newValue)
            if (isSizeToSmall(size)) {
                throw PropertyTooLittleItemsException(this.getRef(parentRefFactory), size, this.minSize!!)
            }
            if (isSizeToBig(size)) {
                throw PropertyTooMuchItemsException(this.getRef(parentRefFactory), size, this.maxSize!!)
            }

            createPropertyValidationUmbrellaException(parentRefFactory) { addException ->
                validateCollectionForExceptions(parentRefFactory, newValue) { item, refFactory ->
                    try {
                        this.valueDefinition.validate(null, item, refFactory)
                    } catch (e: PropertyValidationException) {
                        addException(e)
                    }
                }
            }
        }
    }

    /** Get the size of the collection object */
    abstract fun getSize(newValue: C): Int

    /** Validates the collection content */
    abstract internal fun validateCollectionForExceptions(parentRefFactory: () -> PropertyReference<*, *>?, newValue: C, validator: (item: T, parentRefFactory: () -> PropertyReference<*, *>?) -> Any)

    /** Creates a new mutable instance of the collection */
    abstract override fun newMutableCollection(): MutableCollection<T>

    override fun writeJsonValue(writer: JsonWriter, value: C) {
        writer.writeStartArray()
        value.forEach {
            valueDefinition.writeJsonValue(writer, it)
        }
        writer.writeEndArray()
    }

    override fun readJson(reader: JsonReader): C {
        if (reader.currentToken !is JsonToken.START_ARRAY) {
            throw ParseException("JSON value for $name should be an Array")
        }
        val collection: MutableCollection<T> = newMutableCollection()

        while (reader.nextToken() !is JsonToken.END_ARRAY) {
            collection.add(
                    valueDefinition.readJson(reader)
            )
        }
        @Suppress("UNCHECKED_CAST")
        return collection as C
    }

    override fun calculateTransportByteLengthWithKey(value: C, lengthCacher: (length: ByteLengthContainer) -> Unit): Int {
        var totalByteSize = 0
        when(this.valueDefinition.wireType) {
            WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> {
                // Cache length for length delimiter
                val container = ByteLengthContainer()
                lengthCacher(container)

                value.forEach { item ->
                    totalByteSize += valueDefinition.calculateTransportByteLength(item)
                }
                container.length = totalByteSize

                totalByteSize += ProtoBuf.calculateKeyLength(this.index)
                totalByteSize += container.length.calculateVarByteLength()
            }
            else -> value.forEach { item ->
                totalByteSize += valueDefinition.calculateTransportByteLengthWithKey(this.index, item, lengthCacher)
            }
        }

        return totalByteSize
    }

    override fun writeTransportBytesWithKey(value: C, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        when(this.valueDefinition.wireType) {
            WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> {
                ProtoBuf.writeKey(this.index, WireType.LENGTH_DELIMITED, writer)
                lengthCacheGetter().writeVarBytes(writer)
                value.forEach { item ->
                    valueDefinition.writeTransportBytes(item, writer)
                }
            }
            else -> value.forEach { item ->
                valueDefinition.writeTransportBytesWithKey(this.index, item, lengthCacheGetter, writer)
            }
        }
    }

    override fun isPacked(encodedWireType: WireType) = when(this.valueDefinition.wireType) {
        WireType.BIT_64, WireType.BIT_32, WireType.VAR_INT -> encodedWireType == WireType.LENGTH_DELIMITED
        else -> false
    }

    override fun readCollectionTransportBytes(length: Int, reader: () -> Byte)
            = valueDefinition.readTransportBytes(length, reader)

    override fun readPackedCollectionTransportBytes(length: Int, reader: () -> Byte): C {
        var byteCounter = 0

        val byteReader = {
            byteCounter++
            reader()
        }

        val collection = this.newMutableCollection()

        while (byteCounter < length) {
            collection += valueDefinition.readTransportBytes(length, byteReader)
        }

        @Suppress("UNCHECKED_CAST")
        return collection as C
    }
}