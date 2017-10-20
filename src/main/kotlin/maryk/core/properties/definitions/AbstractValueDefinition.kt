package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.extensions.bytes.computeVarByteSize
import maryk.core.extensions.bytes.writeVarBytes
import maryk.core.json.JsonReader
import maryk.core.json.JsonToken
import maryk.core.json.JsonWriter
import maryk.core.properties.exceptions.ParseException
import maryk.core.protobuf.ProtoBuf
import maryk.core.protobuf.WireType

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
abstract class AbstractValueDefinition<T: Any>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        internal val wireType: WireType
) : AbstractSubDefinition<T>(
        name, index, indexed, searchable, required, final
) {
    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return stored value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    abstract fun readStorageBytes(length: Int, reader:() -> Byte): T

    /** Convert a value to bytes
     * @param value to convert
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    abstract fun writeStorageBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)

    override fun readTransportBytes(length: Int, reader: () -> Byte) = readStorageBytes(length, reader)

    /** Adds length to written bytes
     * @param value to write
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    protected fun writeTransportBytesWithLength(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        this.writeTransportBytes(value, {
            reserver(it + it.computeVarByteSize())
            it.writeVarBytes(writer)
        }, writer)
    }

    /** Convert a value to bytes for transportation
     * @param value to write
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    open fun writeTransportBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        writeStorageBytes(value, reserver, writer)
    }

    override fun writeTransportBytesWithKey(index: Int, value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit) {
        ProtoBuf.writeKey(index, this.wireType, reserver, writer)
        when(this.wireType) {
            WireType.LENGTH_DELIMITED -> writeTransportBytesWithLength(value, reserver, writer)
            else -> writeTransportBytes(value, reserver, writer)
        }
    }

    /** Convert value to String
     * @param value to convert
     * @return value as String
     */
    open fun asString(value: T) = value.toString()

    /**
     * Get the value from a string
     * @param string to convert
     * @return the value
     * @throws ParseException if conversion fails
     */
    @Throws(ParseException::class)
    abstract fun fromString(string: String): T

    /** Writes a value to Json
     * @param value: value to write
     * @param writer: to write json to
     */
    override fun writeJsonValue(writer: JsonWriter, value: T) {
        writer.writeString(
                this.asString(value)
        )
    }

    @Throws(ParseException::class)
    override fun readJson(reader: JsonReader): T {
        if (reader.currentToken !is JsonToken.OBJECT_VALUE && reader.currentToken !is JsonToken.ARRAY_VALUE) {
            throw ParseException("JSON value for $name should be a simple value")
        }
        return this.fromString(reader.lastValue)
    }
}