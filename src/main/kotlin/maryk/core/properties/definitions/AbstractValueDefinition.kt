package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException

/**
 * Abstract Property Definition to define properties.
 *
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained in property
 */
abstract class AbstractValueDefinition<T: Any>(
        name: String?,
        index: Short,
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
    abstract fun convertFromBytes(length: Int, reader:() -> Byte): T

    /** Convert a value to bytes
     * @param value to convert
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    abstract fun convertToBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)

    /** Convert bytes to the defined type
     * @param bytes  to convertFromBytes
     * @param offset where objects starts
     * @param length of objects
     * @return converted objects
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    abstract fun convertFromBytes(bytes: ByteArray, offset: Int, length: Int): T

    /** Convert value to bytes
     * @param value to convertFromBytes
     * @return bytes
     */
    fun convertToBytes(value: T) = this.convertToBytes(value, null, 0)

    /** Convert value to bytes
     * @param value to convertFromBytes
     * @param bytes: to write to
     * @param offset: start position to write to
     * @return bytes
     */
    abstract fun convertToBytes(value: T, bytes: ByteArray?, offset: Int): ByteArray

    /** Convert value to String
     * @param value to convertFromBytes
     * @param optimized true if conversion should be faster to process, false if it should be human readable
     * @return value as String
     */
    open fun convertToString(value: T, optimized: Boolean = false) = value.toString()
}