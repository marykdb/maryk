package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext

/**
 * Property Definition to define numeric properties.
 *
 * Implements methods usefull for numeric definitions
 * @param <T> Type of comparable properties contain
 */
interface IsNumericDefinition<T: Comparable<T>> : IsSimpleDefinition<T, IsPropertyContext> {
    val random:Boolean

    /** @return random value */
    fun createRandom(): T
}