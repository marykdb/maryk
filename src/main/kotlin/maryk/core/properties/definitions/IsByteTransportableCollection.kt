package maryk.core.properties.definitions

/** Interface with methods to read collection items from byte stream
 * @param <T> Type of collection item which is transported
 */
interface IsByteTransportableCollection<T> {
    fun readCollectionTransportBytes(length: Int, reader: () -> Byte): T
}
