package maryk.core.properties.definitions

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.types.IsTemporal

/** Definition for Moment properties of [T] which can be set to now */
interface IsMomentDefinition<T: IsTemporal<T>> : IsComparableDefinition<T, IsPropertyContext> {
    val fillWithNow: Boolean

    /** Create a new value representing current time */
    fun createNow(): T

    companion object {
        internal fun <DO : Any> addFillWithNow(definitions: PropertyDefinitions<DO>, getter: (DO) -> Boolean) {
            definitions.add(7, "fillWithNow", BooleanDefinition(), getter)
        }
    }
}