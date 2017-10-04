package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.json.JsonGenerator
import maryk.core.json.JsonParser
import maryk.core.json.JsonToken
import maryk.core.properties.exceptions.ParseException

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
        final: Boolean
) : AbstractSubDefinition<T>(
        name, index, indexed, searchable, required, final
) {
    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return converted value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    abstract fun convertFromStorageBytes(length: Int, reader:() -> Byte): T

    /** Convert a value to bytes
     * @param value to convert
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    abstract fun convertToStorageBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)

    /** Convert value to String
     * @param value to convert
     * @return value as String
     */
    open fun convertToString(value: T) = value.toString()

    /**
     * Get the value from a string
     * @param string to convert
     * @return the value
     * @throws ParseException if conversion fails
     */
    @Throws(ParseException::class)
    abstract fun convertFromString(string: String): T

    /** Writes a value to Json
     * @param value: value to write
     * @param generator: to write json to
     */
    override fun writeJsonValue(generator: JsonGenerator, value: T) {
        generator.writeString(
                this.convertToString(value)
        )
    }

    @Throws(ParseException::class)
    override fun parseFromJson(parser: JsonParser): T {
        if (parser.currentToken !is JsonToken.OBJECT_VALUE && parser.currentToken !is JsonToken.ARRAY_VALUE) {
            throw ParseException("JSON value for $name should be a simple value")
        }
        return this.convertFromString(parser.lastValue)
    }
}