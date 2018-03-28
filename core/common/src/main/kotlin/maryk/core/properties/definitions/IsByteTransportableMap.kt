package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/** Interface with methods to read map items with key [K] value [V] from byte stream with context [CX] */
interface IsByteTransportableMap<K: Any, V: Any, in CX: IsPropertyContext>
    : IsSerializablePropertyDefinition<Map<K, V>, CX> {

    /** Read the transport bytes from [reader] with [context] into a map key/value pair */
    fun readMapTransportBytes(reader: () -> Byte, context: CX? = null): Pair<K, V>
}
