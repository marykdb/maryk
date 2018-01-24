package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Abstract for reference to a property of type [T] defined by [D]
 */
interface IsPropertyReference<T: Any, out D: IsPropertyDefinition<T>> {
    val completeName: String?
    val propertyDefinition: D

    /** Calculate the transport length of encoding this reference
     * @param cacher to cache length with
     * @return size of this reference part
     */
    fun calculateTransportByteLength(cacher: WriteCacheWriter): Int

    /** Write transport bytes of property reference
     * @param cacheGetter to get next cached length or context
     * @param writer: To write bytes to
     */
    fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit)
}
