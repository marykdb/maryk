package maryk.core.query.filters

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.AbstractValueDefinition
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
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
        override val reference: IsPropertyReference<T, AbstractValueDefinition<T, IsPropertyContext>>,
        val from: T,
        val to: T,
        val inclusiveFrom: Boolean = true,
        val inclusiveTo: Boolean = true
) : IsPropertyCheck<T> {
    override val filterType = FilterType.RANGE

    object Properties {
        val from = ContextualValueDefinition(
                name = "from",
                index = 1,
                contextualResolver = { context: DataModelPropertyContext? ->
                    context!!.reference!!.propertyDefinition
                }
        )
        val to = ContextualValueDefinition(
                name = "to",
                index = 2,
                contextualResolver = { context: DataModelPropertyContext? ->
                    context!!.reference!!.propertyDefinition
                }
        )
        val inclusiveStart = BooleanDefinition(
                name = "inclusiveStart",
                index = 3,
                required = true
        )
        val inclusiveEnd = BooleanDefinition(
                name = "inclusiveEnd",
                index = 4,
                required = true
        )
    }

    companion object: QueryDataModel<Range<*>>(
            definitions = listOf(
                    Def(IsPropertyCheck.Properties.reference, Range<*>::reference),
                    Def(Properties.from, Range<*>::from),
                    Def(Properties.to, Range<*>::to),
                    Def(Properties.inclusiveStart, Range<*>::inclusiveFrom),
                    Def(Properties.inclusiveEnd, Range<*>::inclusiveTo)
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Range(
                reference = map[0] as IsPropertyReference<Any, AbstractValueDefinition<Any, IsPropertyContext>>,
                from = map[1] as Any,
                to = map[2] as Any,
                inclusiveFrom = map[3] as Boolean,
                inclusiveTo = map[4] as Boolean
        )
    }
}