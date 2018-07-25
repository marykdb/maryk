package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
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
    internal object Properties: ObjectPropertyDefinitions<ReferenceValuePair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValuePair<*>::reference
        )
        val value = add(
            2, "value",
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

    internal companion object: SimpleObjectDataModel<ReferenceValuePair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ReferenceValuePair<*>, Properties>) = ReferenceValuePair(
            reference = map(1),
            value = map(2)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(value: T) =
    ReferenceValuePair(this, value)
