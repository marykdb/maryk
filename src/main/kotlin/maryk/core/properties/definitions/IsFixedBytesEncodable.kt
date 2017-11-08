package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.IsDataModel

/** Interface to define something can be en/decoded to fixed byte array */
interface IsFixedBytesEncodable<T: Any> {
    /** The byte size */
    val byteSize: Int

    /** Index of property on model. -1 if not on property */
    val index: Int

    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return stored value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    fun readStorageBytes(length: Int, reader: () -> Byte): T

    /** Calculates the byte size of the storage bytes */
    fun calculateStorageByteLength(value: Boolean) = byteSize

    /** Convert a value to bytes
     * @param context for contextual parameters in dynamic properties
     * @param value to convert
     * @param writer to write bytes to
     */
    fun writeStorageBytes(value: T, writer: (byte: Byte) -> Unit)

    /** Get the value to be used in a key
     * @param dataModel to use to fetch property if relevant
     * @param dataObject to get property from
     */
    fun <DO: Any> getValue(dataModel: IsDataModel<DO>, dataObject: DO): T
}
