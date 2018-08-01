package maryk.core.properties.references

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

typealias AnyPropertyReference = IsPropertyReference<*, *, *>

/**
 * Abstract for reference to a property of type [T] defined by [D]
 */
interface IsPropertyReference<T: Any, out D: IsPropertyDefinition<T>, C: Any> {
    val completeName: String
    val propertyDefinition: D

    /**
     * Calculate the transport length of encoding this reference
     * and stores result in [cacher] if relevant
     */
    fun calculateTransportByteLength(cacher: WriteCacheWriter): Int

    /**
     * Write transport bytes of property reference to [writer] and gets any needed
     * cached values from [cacheGetter]
     */
    fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit)

    /**
     * Resolve the value from the given values
     */
    fun resolve(values: C): T?
}
