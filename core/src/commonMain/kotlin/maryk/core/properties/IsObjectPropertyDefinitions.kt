package maryk.core.properties

import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.values.IsValueItems
import maryk.core.values.MutableValueItems
import maryk.core.values.ValueItem

interface IsObjectPropertyDefinitions<DO>:
    IsPropertyDefinitions,
    Collection<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>> {
    val allWithDefaults: List<IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>>

    override operator fun get(name: String): IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>?
    override operator fun get(index: UInt): IsDefinitionWrapper<Any, Any, IsPropertyContext, DO>?

    fun addSingle(propertyDefinitionWrapper: IsDefinitionWrapper<out Any, *, *, DO>)

    /** Converts a list of optional [pairs] to values */
    fun mapNonNulls(vararg pairs: ValueItem?): IsValueItems =
        MutableValueItems().also { items ->
            for (it in pairs) {
                if (it != null) items += it
            }
        }
}
