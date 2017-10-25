package maryk.core.properties.definitions

import maryk.core.exceptions.DefNotFoundException
import maryk.core.protobuf.ByteSizeContainer

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
    override fun reserveTransportBytesWithKey(value: T, lengthCacher: (size: ByteSizeContainer) -> Unit)
            = this.reserveTransportBytesWithKey(this.index, value, lengthCacher)

    /** Reserve bytes for a value for transportation
     * @param index to write this value for
     * @param value to write
     * @param lengthCacheGetter to fetch next cached length
     * @return total byte size
     */
    abstract fun reserveTransportBytesWithKey(index: Int, value: T, lengthCacher: (size: ByteSizeContainer) -> Unit) : Int

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