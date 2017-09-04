package maryk.core.properties.definitions

/**
 * Abstract Property Definition to define numeric properties.
 *
 * Implements methods usefull for numeric definitions
 * @param <T> Type of comparable properties contain
 */
abstract class AbstractNumericDefinition<T: Comparable<T>>(
        name: String?,
        index: Short,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        unique: Boolean,
        minValue: T?,
        maxValue: T?,
        val random:Boolean
) : AbstractSimpleDefinition<T>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue
) {
    /** @return random value */
    abstract fun createRandom(): T
}