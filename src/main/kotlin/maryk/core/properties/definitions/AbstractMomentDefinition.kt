package maryk.core.properties.definitions

import maryk.core.properties.types.IsTemporal

/**
 * Definition for Moment properties which can be set to now
 * @param <T> Comparable type defining type of moment contained by property
 */
abstract class AbstractMomentDefinition<T: IsTemporal<T>>(
        name: String?,
        index: Short,
        indexed: Boolean,
        searchable: Boolean,
        required: Boolean,
        final: Boolean,
        unique: Boolean,
        minValue: T?,
        maxValue: T?,
        val fillWithNow: Boolean
) : AbstractSimpleDefinition<T>(
        name, index, indexed, searchable, required, final, unique, minValue, maxValue
) {
    /** @return a new value representing the current time */
    abstract fun createNow(): T

    /** Convert the time value to a string
     * @param optimized true for quick to parse solution, false for iso8601 string
     */
    override fun convertToString(value: T, optimized: Boolean) = value.toString(iso8601 = !optimized)
}