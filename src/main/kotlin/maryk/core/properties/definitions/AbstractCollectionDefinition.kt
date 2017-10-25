package maryk.core.properties.definitions

import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.exceptions.ParseException
import maryk.core.properties.exceptions.PropertyTooLittleItemsException
import maryk.core.properties.exceptions.PropertyTooMuchItemsException
import maryk.core.properties.exceptions.PropertyValidationException
import maryk.core.properties.exceptions.createPropertyValidationUmbrellaException
import maryk.core.properties.references.PropertyReference
import maryk.core.protobuf.ByteSizeContainer

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
), HasSizeDefinition, IsByteTransportableCollection<T> {
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

    override fun reserveTransportBytesWithKey(value: C, lengthCacher: (size: ByteSizeContainer) -> Unit): Int {
        var totalByteSize = 0
        value.forEach { item ->
            totalByteSize += valueDefinition.reserveTransportBytesWithKey(this.index, item, lengthCacher)
        }
        return totalByteSize
    }

    override fun writeTransportBytesWithKey(value: C, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit) {
        value.forEach { item ->
            valueDefinition.writeTransportBytesWithKey(this.index, item, lengthCacheGetter, writer)
        }
    }

    override fun readCollectionTransportBytes(length: Int, reader: () -> Byte)
            = valueDefinition.readTransportBytes(length, reader)
}