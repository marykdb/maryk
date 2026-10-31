package maryk.core.properties.definitions.wrapper

import kotlinx.atomicfu.AtomicRef
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.CanHaveComplexChildReference
import maryk.core.properties.references.IsPropertyReference

data class RefCacheEntry(
    val parentRef: AnyPropertyReference?,
    val discriminator: Any?,
    val reference: IsPropertyReference<*, *, *>
)

internal data class ChildRefCacheEntry(
    val owner: CacheableReferenceCreator,
    val discriminator: Any?,
    val reference: IsPropertyReference<*, *, *>
)

@Suppress("UNCHECKED_CAST")
private fun <T : Any, R : IsPropertyReference<T, IsPropertyDefinition<T>, *>> CacheableReferenceCreator.cacheChildReference(
    cache: AtomicRef<PersistentList<ChildRefCacheEntry>?>,
    discriminator: Any?,
    creator: () -> R
): R {
    while (true) {
        val current = cache.value
        current?.firstOrNull { it.owner === this && it.discriminator == discriminator }?.let {
            return it.reference as R
        }
        val reference = creator()
        val entry = ChildRefCacheEntry(this, discriminator, reference)
        val updated = current?.mutate { it.add(entry) } ?: persistentListOf(entry)
        if (cache.compareAndSet(current, updated)) return reference
    }
}

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
        if (parentRef is CanHaveComplexChildReference<*, *, *, *>) {
            // Shared definitions must not retain every newly decoded item parent forever.
            return cacheChildReference(parentRef.childRefCache, discriminator, creator)
        }
        while (true) {
            val currentCache = refCache.value

            // Try to get from cache first
            currentCache?.findCachedReference(parentRef, discriminator)?.let {
                return it as R
            }

            // If not in cache, create new reference
            val newRef = creator()
            val newEntry = RefCacheEntry(parentRef, discriminator, newRef)
            val newCache = currentCache?.mutate { it.add(newEntry) } ?: persistentListOf(newEntry)

            if (refCache.compareAndSet(currentCache, newCache)) {
                return newRef
            }
        }
    }
}
