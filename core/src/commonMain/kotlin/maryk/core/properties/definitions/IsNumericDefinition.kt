package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Property Definition of [T] to define numeric properties.
 *
 * Implements methods useful for numeric definitions
 */
interface IsNumericDefinition<T : Comparable<T>> : IsComparableDefinition<T, IsPropertyContext> {
    val random: Boolean

    /** Create a random value */
    fun createRandom(): T
}
