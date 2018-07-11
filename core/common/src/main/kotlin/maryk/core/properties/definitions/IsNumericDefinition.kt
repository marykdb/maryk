package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions

/**
 * Property Definition of [T] to define numeric properties.
 *
 * Implements methods useful for numeric definitions
 */
interface IsNumericDefinition<T: Comparable<T>> : IsComparableDefinition<T, IsPropertyContext> {
    val random: Boolean

    /** Create a random value */
    fun createRandom(): T

    companion object {
        internal fun <DO:Any> addRandom(index: Int, definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> Boolean) {
            definitions.add(index, "random", BooleanDefinition(default = false), getter)
        }
    }
}
