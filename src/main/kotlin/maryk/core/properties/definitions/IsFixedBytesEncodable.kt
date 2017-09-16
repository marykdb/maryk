package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.objects.DataModel

/** Interface to define something can be en/decoded to fixed byte array */
interface IsFixedBytesEncodable<T: Any> {
    /** The byte size */
    val byteSize: Int

    /** Index of property on model. -1 if not on property */
    val index: Short

    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return converted value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    fun convertFromBytes(length: Int, reader:() -> Byte): T

    /** Convert a value to bytes
     * @param value to convert
     * @param reserver to reserve amount of bytes to write on
     * @param writer to write bytes to
     */
    fun convertToBytes(value: T, reserver: (size: Int) -> Unit, writer: (byte: Byte) -> Unit)

    /** Get the value to be used in a key
     * @param dataModel to use to fetch property if relevant
     * @param dataObject to get property from
     */
    fun <DO: Any> getValue(dataModel: DataModel<DO>, dataObject: DO): T
}
