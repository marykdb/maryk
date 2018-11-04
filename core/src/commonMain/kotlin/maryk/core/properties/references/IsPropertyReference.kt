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

    /** Resolve the value from the given [values] */
    fun resolve(values: C): T?

    /** Get [value] with the reference */
    fun resolveFromAny(value: Any): Any

    /** Unwraps the references into an ordered list with parent to children */
    fun unwrap(): List<IsPropertyReference<*, *, in Any>> {
        @Suppress("UNCHECKED_CAST")
        val refList = mutableListOf(this as IsPropertyReference<*, *, in Any>)
        var ref: IsPropertyReference<*, *, in Any> = this
        while (ref is PropertyReference<*, *, *, *> && ref.parentReference != null) {
            @Suppress("UNCHECKED_CAST")
            ref = ref.parentReference as IsPropertyReference<*, *, in Any>
            refList.add(0, ref)
        }
        return refList
    }
}