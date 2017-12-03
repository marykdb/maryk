package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Checks if reference exists in Value set
 * @param reference to property to compare against
 * @param T: type of value to be operated on
 */
data class ValueIn<T: Any>(
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        val values: Set<T>
) : IsPropertyCheck<T> {
    override val filterType = FilterType.VALUE_IN

    object Properties : PropertyDefinitions<ValueIn<*>>() {
        val values = SetDefinition(
                name = "values",
                index = 1,
                valueDefinition = ContextualValueDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            @Suppress("UNCHECKED_CAST")
                            it!!.reference!!.propertyDefinition
                        }
                )
        )
    }

    companion object: QueryDataModel<ValueIn<*>>(
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, ValueIn<*>::reference),
                    Def(Properties.values, ValueIn<*>::values)
            ),
            properties = object : PropertyDefinitions<ValueIn<*>>() {
                init {
                    IsPropertyCheck.addReference(this, ValueIn<*>::reference)
                    add(1, "values", SetDefinition(
                            valueDefinition = ContextualValueDefinition<DataModelPropertyContext>(
                                    contextualResolver = {
                                        @Suppress("UNCHECKED_CAST")
                                        it!!.reference!!.propertyDefinition
                                    }
                            )
                    ), ValueIn<*>::values)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ValueIn(
                reference = map[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                values = map[1] as Set<Any>
        )
    }
}