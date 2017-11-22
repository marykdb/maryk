package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.protobuf.ByteLengthContainer

/**
 * Abstract Property Definition to define properties.
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained
 */
abstract class AbstractSubDefinition<T: Any, in CX: IsPropertyContext>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean
) : AbstractPropertyDefinition<T>(
        name, index, indexed, searchable, required, final
), IsSerializablePropertyDefinition<T, CX>, IsByteTransportableValue<T, CX> {
    override fun calculateTransportByteLengthWithKey(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?)
            = this.calculateTransportByteLengthWithKey(this.index, value, lengthCacher, context)

    /** Calculates byte length of a value for transportation
     * @param index to write this value for
     * @param value to write
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @param context with context parameters for conversion (for dynamically dependent properties)
     * @return total byte length
     */
    abstract fun calculateTransportByteLengthWithKey(index: Int, value: T, lengthCacher: (length: ByteLengthContainer) -> Unit, context: CX?) : Int

    override fun writeTransportBytesWithKey(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit, context: CX?)
            = this.writeTransportBytesWithIndexKey(this.index, value, lengthCacheGetter, writer, context)

    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @param context with context parameters for conversion (for dynamically dependent properties)
     * @return transported value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    abstract override fun readTransportBytes(length: Int, reader: () -> Byte, context: CX?): T
}