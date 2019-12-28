package maryk.core.properties.definitions.wrapper

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/** To save creation of new references to same fields, the references are cached. */
interface CacheableReferenceCreator {
    val refCache: MutableMap<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>>

    fun <T: Any, R: IsPropertyReference<T, IsPropertyDefinition<T>, *>> cacheRef(
        parentRef: AnyPropertyReference?,
        cache: MutableMap<IsPropertyReference<*, *, *>?, IsPropertyReference<*, *, *>> = this.refCache,
        creator: () -> R
    ): R {
        if (cache.containsKey(parentRef)){
            @Suppress("UNCHECKED_CAST")
            return cache[parentRef] as R
        }

        return creator().also {
            cache[parentRef] = it
        }
    }
}


