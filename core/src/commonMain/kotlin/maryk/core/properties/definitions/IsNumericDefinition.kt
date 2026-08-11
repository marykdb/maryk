package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Legacy numeric property-definition contract.
 *
 * @deprecated Use [IsComparableDefinition] and [IsRandomizableDefinition].
 */
@Deprecated("Use IsComparableDefinition and IsRandomizableDefinition instead.")
interface IsNumericDefinition<T : Comparable<T>> : IsComparableDefinition<T, IsPropertyContext> {
    /** Create a random value. */
    fun createRandom(): T
}
