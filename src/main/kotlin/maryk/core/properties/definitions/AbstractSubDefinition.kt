package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.protobuf.ByteLengthContainer

/**
 * Abstract Property Definition to define properties.
 * This is used for simple single value properties and not for lists and maps.
 * @param <T> Type of objects contained
 */
abstract class AbstractSubDefinition<T: Any>(
        name: String?,
        index: Int,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean
) : AbstractPropertyDefinition<T>(
        name, index, indexed, searchable, required, final) {
    override fun calculateTransportByteLengthWithKey(value: T, lengthCacher: (length: ByteLengthContainer) -> Unit)
            = this.calculateTransportByteLengthWithKey(this.index, value, lengthCacher)

    /** Reserve bytes for a value for transportation
     * @param index to write this value for
     * @param value to write
     * @param lengthCacher to cache calculated lengths. Ordered so it can be read back in the same order
     * @return total byte length
     */
    abstract fun calculateTransportByteLengthWithKey(index: Int, value: T, lengthCacher: (length: ByteLengthContainer) -> Unit) : Int

    override fun writeTransportBytesWithKey(value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit)
            = this.writeTransportBytesWithKey(this.index, value, lengthCacheGetter, writer)

    /** Convert a value to bytes for transportation and adds the key with tag and wiretype
     * @param index to write this value for
     * @param value to write
     * @param lengthCacheGetter to fetch next cached length
     * @param writer to write bytes to
     */
    abstract fun writeTransportBytesWithKey(index: Int, value: T, lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit)

    /** Convert to value from a byte reader
     * @param length of bytes to read
     * @param reader to read bytes from
     * @return transported value
     * @throws DefNotFoundException if definition is not found to translate bytes
     */
    @Throws(DefNotFoundException::class)
    abstract fun readTransportBytes(length: Int, reader:() -> Byte): T
}