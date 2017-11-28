package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ByteLengthContainer
import maryk.core.protobuf.WireType

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
abstract class AbstractSimpleValueDefinition<T: Any, in CX: IsPropertyContext>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        wireType: WireType
) : AbstractValueDefinition<T, CX>(
        name, index, indexed, searchable, required, final, wireType
) {
    /** Convert to value from a byte reader
     * @param context for contextual parameters
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return stored value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    abstract fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculate byte length of a value
     * @param value to calculate length of
     */
    abstract fun calculateStorageByteLength(value: T): Int

    /** Convert a value to bytes
     * @param value to convert
     * @param writer to write bytes to
     */
    abstract fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) = readStorageBytes(length, reader)

    override fun writeTransportBytes(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?) {
        writeStorageBytes(value, writer)
    }

    final override fun calculateTransportByteLength(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = this.calculateTransportByteLength(value)

    /** Calculates the needed bytes to transport the value
     * @param value to get length of
     * @return the total length
     */
    abstract fun calculateTransportByteLength(value: T): Int

    /** Convert value to String
     * @param value to convert
     * @return value as String
     */
    open fun asString(value: T) = value.toString()

    override fun asString(value: T, context: CX?) = this.asString(value)

    /**
     * Get the value from a string
     * @param string to convert
     * @return the value
     * @throws ParseException when encountering unparsable content
     */
    abstract internal fun fromString(string: String): T

    override final fun fromString(string: String, context: CX?) = this.fromString(string)

    override fun writeJsonValue(value: T, writer: JsonWriter, context: CX?) {
        writer.writeString(
                this.asString(value, context)
        )
    }

    override fun readJson(reader: JsonReader, context: CX?): T {
        if (reader.currentToken !is JsonToken.OBJECT_VALUE && reader.currentToken !is JsonToken.ARRAY_VALUE) {
            throw ParseException("JSON value for $name should be a simple value")
        }
        return this.fromString(reader.lastValue, context)
    }
}