package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
interface IsSimpleValueDefinition<T: Any, in CX: IsPropertyContext> : IsValueDefinition<T, CX> {
    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return stored value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculate byte length of a value
     * @param value to calculate length of
     */
    fun calculateStorageByteLength(value: T): Int

    /** Convert a value to bytes
     * @param value to convert
     * @param writer to write bytes to
     */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?) = readStorageBytes(length, reader)

    override fun writeTransportBytes(value: T, cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit, context: CX?) {
        writeStorageBytes(value, writer)
    }

    override fun calculateTransportByteLength(value: T, cacher: WriteCacheWriter, context: CX?)
            = this.calculateTransportByteLength(value)

    /** Calculates the needed bytes to transport the value
     * @param value to get length of
     * @return the total length
     */
    fun calculateTransportByteLength(value: T): Int

    /** Convert value to String
     * @param value to convert
     * @return value as String
     */
    fun asString(value: T) = value.toString()

    override fun asString(value: T, context: CX?) = this.asString(value)

    /**
     * Get the value from a string
     * @param string to convert
     * @return the value
     * @throws ParseException when encountering unparsable content
     */
    fun fromString(string: String): T

    override fun fromString(string: String, context: CX?) = this.fromString(string)

    override fun writeJsonValue(value: T, writer: JsonWriter, context: CX?) {
        writer.writeString(
                this.asString(value, context)
        )
    }

    override fun readJson(reader: JsonReader, context: CX?): T {
        if (reader.currentToken !is JsonToken.ObjectValue && reader.currentToken !is JsonToken.ArrayValue) {
            throw ParseException("JSON value should be a simple value")
        }
        return this.fromString(reader.lastValue, context)
    }
}