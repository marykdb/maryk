package maryk.core.values

import maryk.core.properties.references.AnyPropertyReference

/**
 * Optional extension for getters which can enumerate collection members without
 * materializing the parent Map or Set.
 *
 * Returning `false` without emitting values keeps the caller on the regular
 * [IsValuesGetter] path. Implementations must emit each collection member once
 * in collection order and may be invoked repeatedly for the same parent
 * reference. Exceptions from [emit] must propagate and abort enumeration.
 */
interface IsStreamingValuesGetter : IsValuesGetter {
    fun streamMapKeys(parent: AnyPropertyReference, emit: (Any) -> Unit): Boolean

    fun streamSetValues(parent: AnyPropertyReference, emit: (Any) -> Unit): Boolean
}
