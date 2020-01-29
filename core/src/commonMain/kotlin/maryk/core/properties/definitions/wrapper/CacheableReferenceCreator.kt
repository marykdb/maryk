package maryk.core.properties.definitions.wrapper

import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.concurrency.value
import co.touchlab.stately.freeze
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceWithParent

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
            val newArray = cache.value?.let { arrayOf(created, *it) } ?: arrayOf<IsPropertyReference<*, *, *>>(created)
            cache.value = newArray.freeze()
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
