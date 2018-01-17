package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32

/** Interface to define something can be en/decoded to fixed byte array */
interface IsFixedBytesEncodable<T: Any> {
    /** The byte size */
    val byteSize: Int

    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return stored value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculates the byte size of the storage bytes */
    fun calculateStorageByteLength(value: T) = byteSize

    /** Convert a value to bytes
     * @param value to convert
     * @param writer to write bytes to
     */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    companion object {
        internal fun <DO:Any> addByteSize(index: Int, definitions: PropertyDefinitions<DO>, getter: (DO) -> Int) {
            definitions.add(index, "byteSize", NumberDefinition(type = UInt32)) { getter(it).toUInt32() }
        }
    }
}
