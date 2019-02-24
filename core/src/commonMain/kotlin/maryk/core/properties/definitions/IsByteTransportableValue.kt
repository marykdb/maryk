package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext

/** Interface with methods to read value items of [T] from byte stream with context [CX] */
interface IsByteTransportableValue<T : Any, in CX : IsPropertyContext> : IsSerializablePropertyDefinition<T, CX> {
    /**
     * Read value from [reader] with [context] until [length] is reached
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    fun readTransportBytes(length: Int, reader: () -> Byte, context: CX? = null): T
}
