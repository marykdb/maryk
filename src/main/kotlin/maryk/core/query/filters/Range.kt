package maryk.core.query.filters

import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsDataObjectValueProperty
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Checks if reference is within given range
 * @param reference to property to compare against
 * @param from which value the range should start
 * @param to which value the range should reach
 * @param inclusiveFrom if true (default) the from value will be included in the range
 * @param inclusiveTo if true (default) the to value will be included in the range.
 * @param T: type of value to be operated on
 */
data class Range<T: Any>(
        override val reference: IsPropertyReference<T, IsDataObjectValueProperty<T, IsPropertyContext, *>>,
        val from: T,
        val to: T,
        val inclusiveFrom: Boolean = true,
        val inclusiveTo: Boolean = true
) : IsPropertyCheck<T> {
    override val filterType = FilterType.RANGE

    companion object: QueryDataModel<Range<*>>(
            properties = object : PropertyDefinitions<Range<*>>() {
                init {
                    IsPropertyCheck.addReference(this, Range<*>::reference)
                    add(1, "from", ContextualValueDefinition(
                            contextualResolver = { context: DataModelPropertyContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context!!.reference!!.propertyDefinition.property as AbstractValueDefinition<Any, IsPropertyContext>
                            }
                    ), Range<*>::from)

                    add(2, "to", ContextualValueDefinition(
                            contextualResolver = { context: DataModelPropertyContext? ->
                                @Suppress("UNCHECKED_CAST")
                                context!!.reference!!.propertyDefinition.property as AbstractValueDefinition<Any, IsPropertyContext>
                            }
                    ), Range<*>::to)

                    add(3, "inclusiveFrom", BooleanDefinition(), Range<*>::inclusiveFrom)
                    add(4, "inclusiveTo", BooleanDefinition(), Range<*>::inclusiveTo)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Range(
                reference = map[0] as IsPropertyReference<Any, IsDataObjectValueProperty<Any, IsPropertyContext, *>>,
                from = map[1] as Any,
                to = map[2] as Any,
                inclusiveFrom = map[3] as Boolean,
                inclusiveTo = map[4] as Boolean
        )
    }
}