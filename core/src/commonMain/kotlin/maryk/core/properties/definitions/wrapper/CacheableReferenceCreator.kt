package maryk.core.properties.definitions.wrapper

import kotlinx.atomicfu.AtomicRef
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

data class RefCacheEntry(
    val parentRef: AnyPropertyReference?,
    val discriminator: Any?,
    val reference: IsPropertyReference<*, *, *>
)

private fun PersistentList<RefCacheEntry>.findCachedReference(
    parentRef: AnyPropertyReference?,
    discriminator: Any?
): IsPropertyReference<*, *, *>? {
    for (entry in this) {
        if (entry.parentRef === parentRef && entry.discriminator == discriminator) {
            return entry.reference
        }
    }
    return null
}

/** To save creation of new references to same fields, the references are cached. */
interface CacheableReferenceCreator {
    val refCache: AtomicRef<PersistentList<RefCacheEntry>?>

    @Suppress("UNCHECKED_CAST")
    fun <T: Any, R: IsPropertyReference<T, IsPropertyDefinition<T>, *>> cacheRef(
        parentRef: AnyPropertyReference?,
        discriminator: Any? = null,
        creator: () -> R
    ): R {
        while (true) {
            val currentCache = refCache.value

            // Try to get from cache first
            currentCache?.findCachedReference(parentRef, discriminator)?.let {
                return it as R
            }

            // If not in cache, create new reference
            val newRef = creator()
            val newEntry = RefCacheEntry(parentRef, discriminator, newRef)
            val newCache = currentCache?.add(newEntry) ?: persistentListOf(newEntry)

            if (refCache.compareAndSet(currentCache, newCache)) {
                return newRef
            }
        }
    }
}
