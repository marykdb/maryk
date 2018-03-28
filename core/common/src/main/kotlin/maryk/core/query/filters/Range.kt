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

/**
 * Checks if [reference] is within given range of [from] until [to] of type [T].
 * With [inclusiveFrom] and [inclusiveTo] set to true (default) it will search the range including [from] or [to]
 */
data class Range<T: Any>(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, IsPropertyContext, *>>,
    val from: T,
    val to: T,
    val inclusiveFrom: Boolean = true,
    val inclusiveTo: Boolean = true
) : IsPropertyCheck<T> {
    override val filterType = FilterType.RANGE

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
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Range(
            reference = map[0] as IsPropertyReference<Any, IsValuePropertyDefinitionWrapper<Any, IsPropertyContext, *>>,
            from = map[1] as Any,
            to = map[2] as Any,
            inclusiveFrom = map[3] as Boolean,
            inclusiveTo = map[4] as Boolean
        )
    }
}