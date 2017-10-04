package maryk.core.properties.definitions

import maryk.core.properties.types.IsTemporal

/**
 * Definition for Moment properties which can be set to now
 * @param <T> Comparable type defining type of moment contained by property
 */
abstract class AbstractMomentDefinition<T: IsTemporal<T>>(
        name: String?,
        index: Int,
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
}