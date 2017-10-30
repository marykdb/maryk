package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WireType

/** Interface with methods to read collection items from byte stream
 * @param <T> Type of collection item which is transported
 * @param <T> Collection containing type T
 */
interface IsByteTransportableCollection<T: Any, out C: Collection<T>, in CX: IsPropertyContext> {
    /** Reads the transport bytes of a collection
     * @param length to read
     * @param reader to read with
     */
    fun readCollectionTransportBytes(context: CX?, length: Int, reader: () -> Byte): T

    /** Reads the packed transport bytes of a collection
     * @param length to read
     * @param reader to read with
     */
    fun readPackedCollectionTransportBytes(context: CX?, length: Int, reader: () -> Byte): C

    /** Creates a new mutable collection of type T */
    fun newMutableCollection(): MutableCollection<T>

    /** True if encoded bytes are packed
     * Packed is true when encoded as LENGTH_DELIMITED while value should be VAR_INT/BIT_64/BIT_32
     * @param encodedWireType wiretype of encoded bytes
     */
    fun isPacked(encodedWireType: WireType): Boolean
}
