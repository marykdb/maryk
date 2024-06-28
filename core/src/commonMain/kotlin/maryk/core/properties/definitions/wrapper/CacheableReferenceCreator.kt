package maryk.core.properties.definitions.wrapper

import kotlinx.atomicfu.AtomicRef
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/** To save creation of new references to same fields, the references are cached. */
interface CacheableReferenceCreator {
    val refCache: AtomicRef<PersistentMap<String, IsPropertyReference<*, *, *>>?>

    @Suppress("UNCHECKED_CAST")
    fun <T: Any, R: IsPropertyReference<T, IsPropertyDefinition<T>, *>> cacheRef(
        parentRef: AnyPropertyReference?,
        keyGenerator: (AnyPropertyReference?) -> String = { it?.completeName ?: "-" },
        creator: () -> R
    ): R {
        val key = keyGenerator(parentRef)

        while (true) {
            val currentCache = refCache.value

            // Try to get from cache first
            currentCache?.get(key)?.let {
                return it as R
            }

            // If not in cache, create new reference
            val newRef = creator()
            val newCache = currentCache?.put(key, newRef) ?: persistentMapOf(key to newRef)

            if (refCache.compareAndSet(currentCache, newCache)) {
                return newRef
            }
        }
    }
}
