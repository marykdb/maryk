package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.WireType

/** Interface with methods to read collection of type[C] containing items of [T]
 * from byte stream with context [CX].
 */
interface IsByteTransportableCollection<T: Any, C: Collection<T>, in CX: IsPropertyContext> : IsSerializablePropertyDefinition<C, CX> {
    /**
     * Reads the transport bytes until length from [reader] into a collection
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun readCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX? = null): T

    /**
     * Reads the packed transport bytes from [reader] until [length] into a collection
     * Optionally pass a [context] to read more complex properties which depend on other properties
     */
    fun readPackedCollectionTransportBytes(length: Int, reader: () -> Byte, context: CX? = null): C

    /**
     * Creates a new mutable collection of type T
     * Pass a [context] to read more complex properties which depend on other properties
     */
    fun newMutableCollection(context: CX?): MutableCollection<T>

    /**
     * Packed is true when encoded as LENGTH_DELIMITED while [encodedWireType] should be VAR_INT/BIT_64/BIT_32
     * Pass a [context] to check more complex properties which depend on other properties
     */
    fun isPacked(context: CX?, encodedWireType: WireType): Boolean
}
