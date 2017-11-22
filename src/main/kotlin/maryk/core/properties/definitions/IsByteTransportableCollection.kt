package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WireType

/** Interface with methods to read collection items from byte stream
 * @param <T> Type of collection item which is transported
 * @param <T> Collection containing type T
 */
interface IsByteTransportableCollection<T: Any, C: Collection<T>, in CX: IsPropertyContext> : IsSerializablePropertyDefinition<C, CX> {
    /** Reads the transport bytes of a collection
     * @param context with possible context values for Dynamic property writers
     * @param length to read
     * @param reader to read with
     */
    fun readCollectionTransportBytes(context: CX?, length: Int, reader: () -> Byte): T

    /** Reads the packed transport bytes of a collection
     * @param context with possible context values for Dynamic property writers
     * @param length to read
     * @param reader to read with
     */
    fun readPackedCollectionTransportBytes(context: CX?, length: Int, reader: () -> Byte): C

    /** Creates a new mutable collection of type T
     * @param context with possible context values for Dynamic property writers
     */
    fun newMutableCollection(context: CX?): MutableCollection<T>

    /** True if encoded bytes are packed
     * Packed is true when encoded as LENGTH_DELIMITED while value should be VAR_INT/BIT_64/BIT_32
     * @param context with possible context values for Dynamic property writers
     * @param encodedWireType wiretype of encoded bytes
     */
    fun isPacked(context: CX?, encodedWireType: WireType): Boolean
}
