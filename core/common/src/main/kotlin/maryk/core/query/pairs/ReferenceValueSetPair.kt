package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Compares given [values] set of type [T] against referenced value [reference] */
data class ReferenceValueSetPair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>,
    val values: Set<T>
) : DefinedByReference<T> {
    internal object Properties: PropertyDefinitions<ReferenceValueSetPair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValueSetPair<*>::reference
        )
        val values = add(1, "values", SetDefinition(
            valueDefinition = ContextualValueDefinition(
                contextualResolver = { context: DataModelPropertyContext? ->
                    context?.reference?.let {
                        @Suppress("UNCHECKED_CAST")
                        it.propertyDefinition.definition as IsValueDefinition<Any, IsPropertyContext>
                    } ?: throw ContextNotFoundException()
                }
            )
        ), ReferenceValueSetPair<*>::values)
    }

    internal companion object: SimpleDataModel<ReferenceValueSetPair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: DataObjectMap<ReferenceValueSetPair<*>>) = ReferenceValueSetPair(
            reference = map(0),
            values = map(1)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(value: Set<T>) =
    ReferenceValueSetPair(this, value)
