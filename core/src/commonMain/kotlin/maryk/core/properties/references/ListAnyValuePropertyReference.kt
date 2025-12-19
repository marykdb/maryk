package maryk.core.properties.references

import maryk.core.exceptions.UnexpectedValueException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsListDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSubDefinition
import maryk.core.properties.definitions.SubListDefinition
import maryk.core.protobuf.WriteCacheReader
import maryk.core.protobuf.WriteCacheWriter

/**
 * Property reference which collects the values from any item in a list by
 * delegating to an underlying [reference] and turning the resolved results into
 * a list.
 */
class ListAnyValuePropertyReference<T : Any> internal constructor(
    private val reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>
) : IsPropertyReference<List<T>, IsListDefinition<T, IsPropertyContext>, Any> {
    private val listDefinition: IsListDefinition<T, IsPropertyContext> = run {
        val comparable = reference.comparablePropertyDefinition
        require(comparable is IsSubDefinition<*, *>) {
            "List references can only be created for sub definitions"
        }
        @Suppress("UNCHECKED_CAST")
        SubListDefinition(comparable as IsSubDefinition<T, IsPropertyContext>)
    }

    override val completeName get() = reference.completeName
    override val propertyDefinition get() = listDefinition
    override val comparablePropertyDefinition get() = listDefinition

    override fun calculateTransportByteLength(cacher: WriteCacheWriter) =
        reference.calculateTransportByteLength(cacher)

    override fun writeTransportBytes(cacheGetter: WriteCacheReader, writer: (byte: Byte) -> Unit) =
        reference.writeTransportBytes(cacheGetter, writer)

    override fun calculateSelfStorageByteLength() = reference.calculateSelfStorageByteLength()

    override fun writeSelfStorageBytes(writer: (byte: Byte) -> Unit) = reference.writeSelfStorageBytes(writer)

    override fun calculateStorageByteLength() = reference.calculateStorageByteLength()

    override fun writeStorageBytes(writer: (byte: Byte) -> Unit) = reference.writeStorageBytes(writer)

    override fun resolve(values: Any): List<T>? = resolveToList(values)

    override fun resolveFromAny(value: Any): Any =
        resolveToList(value) ?: throw UnexpectedValueException("Not Found ${reference.completeName} on Values")

    override fun unwrap(listToUse: MutableList<IsPropertyReference<*, *, *>>?) = reference.unwrap(listToUse)

    private fun resolveToList(start: Any?): List<T>? {
        if (start == null) return null

        var current: Any = start
        var fuzzy = false
        val path = reference.unwrap(null)

        for (toResolve in path) {
            current = if (fuzzy) {
                val items = current as? Collection<*> ?: return null
                val collected = mutableListOf<Any>()
                for (item in items) {
                    val resolved = item?.let { toResolve.resolveFromAny(it) } ?: continue
                    if (resolved is Collection<*>) {
                        for (child in resolved) {
                            if (child != null) {
                                collected += child
                            }
                        }
                    } else {
                        collected += resolved
                    }
                }
                collected
            } else {
                toResolve.resolveFromAny(current)
            }

            if (!fuzzy && toResolve is IsFuzzyReference) {
                fuzzy = true
            }
        }

        val result = current

        @Suppress("UNCHECKED_CAST")
        return when (result) {
            is List<*> -> result as List<T>
            is Collection<*> -> result.filterNotNull() as List<T>
            else -> listOf(result as T)
        }
    }

    override fun equals(other: Any?) =
        this === other || (other is ListAnyValuePropertyReference<*> && reference == other.reference)

    override fun hashCode() = reference.hashCode()

    override fun toString() = reference.toString()
}
