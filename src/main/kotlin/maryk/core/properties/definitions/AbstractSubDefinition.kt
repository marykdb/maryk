package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext

/**
 * Abstract Property Definition to define properties.
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained
 */
abstract class AbstractSubDefinition<T: Any, in CX: IsPropertyContext>(
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean
) : AbstractPropertyDefinition<T>(
        indexed, searchable, required, final
), IsSerializablePropertyDefinition<T, CX>, IsByteTransportableValue<T, CX> {
    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @param context with context parameters for conversion (for dynamically dependent properties)
     * @return transported value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    abstract override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): T
}