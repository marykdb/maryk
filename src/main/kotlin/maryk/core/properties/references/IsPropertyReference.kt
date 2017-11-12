package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.ByteLengthContainer

/**
 * Abstract for reference to a property
 * @param <T> Type of reference
 * @param <D> Definition of property
 */
interface IsPropertyReference<T: Any, out D: IsPropertyDefinition<T>> {
    val completeName: String?
    val propertyDefinition: D

    /** Calculate the transport length of encoding this reference
     * @param lengthCacher to cache length with
     */
    fun calculateTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int

    /** Calculate the transport length of encoding this reference
     * For cascading use
     * @param lengthCacher to cache length with
     * @return size of this reference part
     */
    fun calculateSubTransportByteLength(lengthCacher: (length: ByteLengthContainer) -> Unit): Int

    /** Write transport bytes of property reference
     * @param lengthCacheGetter to get next cached length
     * @param writer: To write bytes to
     */
    fun writeTransportBytes(lengthCacheGetter: () -> Int, writer: (byte: Byte) -> Unit)
}
