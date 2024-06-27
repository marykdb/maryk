package maryk.core.properties.definitions.wrapper

import kotlinx.atomicfu.AtomicRef
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/** To save creation of new references to same fields, the references are cached. */
interface CacheableReferenceCreator {
    val refCache: AtomicRef<Map<String, IsPropertyReference<*, *, *>>>

    @Suppress("UNCHECKED_CAST")
    fun <T: Any, R: IsPropertyReference<T, IsPropertyDefinition<T>, *>> cacheRef(
        parentRef: AnyPropertyReference?,
        cache: AtomicRef<Map<String, IsPropertyReference<*, *, *>>> = this.refCache,
        keyGenerator: (AnyPropertyReference?) -> String = { it?.completeName ?: "-" },
        creator: () -> R
    ): R {
        val key = keyGenerator(parentRef)

        cache.value[key]?.let {
            return it as R
        }

        return creator().also { created ->
            while (true) {
                val currentCache = cache.value
                currentCache[key]?.let { return it as R }
                val newCache = currentCache + (key to created)
                if (cache.compareAndSet(currentCache, newCache)) {
                    break
                }
            }
        }
    }
}
