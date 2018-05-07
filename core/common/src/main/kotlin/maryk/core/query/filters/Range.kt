package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.BooleanDefinition
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Checks if reference is within given [range] */
infix fun <T: Comparable<T>> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.inRange(
    range: ClosedRange<T>
) = Range(this, range.start, range.endInclusive, true, true)

/** Checks if reference is within given [range] */
fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.inRange(
    from: T,
    to: T,
    inclusiveFrom: Boolean,
    inclusiveTo: Boolean
) = Range(this, from, to, inclusiveFrom, inclusiveTo)

/**
 * Checks if [reference] is within given range of [from] until [to] of type [T].
 * With [inclusiveFrom] and [inclusiveTo] set to true (default) it will search the range including [from] or [to]
 */
data class Range<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val from: T,
    val to: T,
    val inclusiveFrom: Boolean = true,
    val inclusiveTo: Boolean = true
) : IsPropertyCheck<T> {
    override val filterType = FilterType.Range

    internal companion object: QueryDataModel<Range<*>>(
        properties = object : PropertyDefinitions<Range<*>>() {
            init {
                IsPropertyCheck.addReference(this, Range<*>::reference)
                add(1, "from", ContextualValueDefinition(
                    contextualResolver = { context: DataModelPropertyContext? ->
                        @Suppress("UNCHECKED_CAST")
                        context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                            ?: throw ContextNotFoundException()
                    }
                ), Range<*>::from)

                add(2, "to", ContextualValueDefinition(
                    contextualResolver = { context: DataModelPropertyContext? ->
                        @Suppress("UNCHECKED_CAST")
                        context?.reference?.propertyDefinition?.definition as IsValueDefinition<Any, IsPropertyContext>?
                                ?: throw ContextNotFoundException()
                    }
                ), Range<*>::to)

                add(3, "inclusiveFrom", BooleanDefinition(), Range<*>::inclusiveFrom)
                add(4, "inclusiveTo", BooleanDefinition(), Range<*>::inclusiveTo)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = Range(
            reference = map(0),
            from = map(1),
            to = map(2),
            inclusiveFrom = map(3),
            inclusiveTo = map(4)
        )
    }
}
