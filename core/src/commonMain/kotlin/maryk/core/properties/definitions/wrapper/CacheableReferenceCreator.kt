package maryk.core.properties.definitions.wrapper

import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.lib.concurrency.AtomicReference
import maryk.lib.freeze

/** To save creation of new references to same fields, the references are cached. */
interface CacheableReferenceCreator {
    val refCache: AtomicReference<Array<IsPropertyReference<*, *, *>>?>

    fun <T: Any, R: IsPropertyReference<T, IsPropertyDefinition<T>, *>> cacheRef(
        parentRef: AnyPropertyReference?,
        cache: AtomicReference<Array<IsPropertyReference<*, *, *>>?> = this.refCache,
        matcher: (R) -> Boolean = createParentMatcher(parentRef),
        creator: () -> R
    ): R {
        @Suppress("UNCHECKED_CAST")
        (cache.get() as Array<R>?)?.firstOrNull(matcher)?.let {
            return it
        }

        return creator().also { created ->
            val newArray = cache.get()?.let { arrayOf(created, *it) } ?: arrayOf<IsPropertyReference<*, *, *>>(created)
            cache.set(newArray.freeze())
        }
    }
}

internal fun createParentMatcher(parentRef: AnyPropertyReference?): (IsPropertyReference<*, *, *>) -> Boolean = {
    when (it) {
        is IsPropertyReferenceWithParent<*, *, *, *> ->
            it.parentReference == parentRef
        else -> parentRef == null
    }
}
