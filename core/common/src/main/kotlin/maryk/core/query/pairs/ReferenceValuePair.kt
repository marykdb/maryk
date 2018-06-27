package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Compares given [value] of type [T] against referenced value [reference] */
data class ReferenceValuePair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val value: T
) : DefinedByReference<T> {
    internal object Properties: PropertyDefinitions<ReferenceValuePair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValuePair<*>::reference
        )
        val value = add(
            1, "value",
            ContextualValueDefinition(
                contextualResolver = { context: DataModelPropertyContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            ),
            ReferenceValuePair<*>::value
        )
    }

    internal companion object: SimpleDataModel<ReferenceValuePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: DataObjectMap<ReferenceValuePair<*>>) = ReferenceValuePair(
            reference = map(0),
            value = map(1)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(value: T) =
    ReferenceValuePair(this, value)
