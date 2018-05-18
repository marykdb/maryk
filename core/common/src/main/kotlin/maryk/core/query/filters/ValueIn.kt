package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.SimpleFilterDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.json.IsJsonLikeWriter

/** Checks if reference exists in set with [values] of type [T] */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.valueIsIn(
    values: Set<T>
) = ValueIn(this, values)

/** Checks if [reference] exists in set with [values] of type [T] */
data class ValueIn<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val values: Set<T>
) : IsPropertyCheck<T> {
    override val filterType = FilterType.ValueIn

    internal object Properties : PropertyDefinitions<ValueIn<*>>() {
        val reference = IsPropertyCheck.addReference(this, ValueIn<*>::reference)

        val values = add(1, "values", SetDefinition(
            valueDefinition = ContextualValueDefinition(
                contextualResolver = { context: DataModelPropertyContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        ), ValueIn<*>::values)
    }

    internal companion object: SimpleFilterDataModel<ValueIn<*>>(
        properties = Properties
    ) {
        override fun invoke(map: Map<Int, *>) = ValueIn(
            reference = map(0),
            values = map(1)
        )

        override fun writeJson(obj: ValueIn<*>, writer: IsJsonLikeWriter, context: DataModelPropertyContext?) {
            writer.writeJsonValues(Properties.reference, obj.reference, Properties.values, obj.values, context)
        }
    }
}
