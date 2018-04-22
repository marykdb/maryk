package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Checks if reference exists in set with [values] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>.valueIsIn(
    values: Set<T>
) = ValueIn(this, values)

/** Checks if [reference] exists in set with [values] of type [T] */
data class ValueIn<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
    val values: Set<T>
) : IsPropertyCheck<T> {
    override val filterType = FilterType.VALUE_IN

    internal companion object: QueryDataModel<ValueIn<*>>(
        properties = object : PropertyDefinitions<ValueIn<*>>() {
            init {
                IsPropertyCheck.addReference(this, ValueIn<*>::reference)
                add(1, "values", SetDefinition(
                    valueDefinition = ContextualValueDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.reference?.let {
                                @Suppress("UNCHECKED_CAST")
                                it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                            } ?: throw ContextNotFoundException()
                        }
                    )
                ), ValueIn<*>::values)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ValueIn(
            reference = map[0] as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, IsPropertyContext, *>>,
            values = map[1] as Set<Any>
        )
    }
}
