package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleObjectDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.SetDefinition
import maryk.core.properties.definitions.contextual.ContextualValueDefinition
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext
import maryk.core.query.DefinedByReference

/** Compares given [values] set of type [T] against referenced value [reference] */
data class ReferenceValueSetPair<T: Any> internal constructor(
    override val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>, *>,
    val values: Set<T>
) : DefinedByReference<T> {
    object Properties: ObjectPropertyDefinitions<ReferenceValueSetPair<*>>() {
        val reference = DefinedByReference.addReference(
            this,
            ReferenceValueSetPair<*>::reference
        )
        val values = add(2, "values", SetDefinition(
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

    companion object: SimpleObjectDataModel<ReferenceValueSetPair<*>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ReferenceValueSetPair<*>, Properties>) = ReferenceValueSetPair(
            reference = map(1),
            values = map(2)
        )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>, *>.with(value: Set<T>) =
    ReferenceValueSetPair(this, value)
