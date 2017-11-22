package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext

/** Interface with methods to read value items from byte stream
 * @param T Type of value
 * @param CX Context type of map
 */
interface IsByteTransportableValue<T: Any, in CX: IsPropertyContext> : IsSerializablePropertyDefinition<T, CX> {
    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @param context with context parameters for conversion (for dynamically dependent properties)
     * @return transported value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    fun readTransportBytes(length: Int, reader: () -> Byte, context: CX? = null): T
}
