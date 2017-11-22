package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Interface with methods to read map items from byte stream
 * @param K Key type of map
 * @param V Value type of map
 * @param CX Context type of map
 */
interface IsByteTransportableMap<K: Any, V: Any, in CX: IsPropertyContext> : IsSerializablePropertyDefinition<Map<K, V>, CX> {
    /** Read the transport bytes as a map
     * @param reader to read bytes with for map
     * @param context for contextual parameters in reading dynamic properties
     * @return Pair of key value
     */
    fun readMapTransportBytes(reader: () -> Byte, context: CX? = null): Pair<K, V>
}
