package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.IsTemporal

/**
 * Definition for Moment properties which can be set to now
 * @param <T> Comparable type defining type of moment contained by property
 */
interface IsMomentDefinition<T: IsTemporal<T>> : IsComparableDefinition<T, IsPropertyContext> {
    val fillWithNow: Boolean

    /** @return a new value representing the current time */
    fun createNow(): T

    companion object {
        internal fun <DO : Any> addFillWithNow(definitions: PropertyDefinitions<DO>, getter: (DO) -> Boolean) {
            definitions.add(7, "fillWithNow", BooleanDefinition(), getter)
        }
    }
}