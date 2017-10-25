package maryk.core.properties.definitions

/** Interface with methods to read collection items from byte stream
 * @param <T> Type of collection item which is transported
 */
interface IsByteTransportableCollection<T: Any> {
    /** Reads the transport bytes of a collection
     * @param length to read
     * @param reader to read with
     */
    fun readCollectionTransportBytes(length: Int, reader: () -> Byte): T

    /** Creates a new mutable collection of type T */
    fun newMutableCollection(): MutableCollection<T>
}
