package maryk.core.properties.references

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.PersistentList
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.wrapper.ChildRefCacheEntry

/**
 * Reference to properties of type [T] defined by [D] which can have simple children
 * Has an optional [parentReference]
 */
abstract class CanHaveComplexChildReference<
    T : Any,
    out D : IsPropertyDefinition<T>,
    out P : AnyPropertyReference,
    C : Any
> internal constructor(
    definition: D,
    parentReference: P?
) : CanHaveSimpleChildReference<T, D, P, C>(definition, parentReference) {
    // Child references share this parent's lifetime, including transient decoded collection items.
    internal val childRefCache: AtomicRef<PersistentList<ChildRefCacheEntry>?> = atomic(null)
}
