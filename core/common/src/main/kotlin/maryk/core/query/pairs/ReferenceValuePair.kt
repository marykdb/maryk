package maryk.core.query.pairs

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.SimpleDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
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
        override fun invoke(map: Map<Int, *>) = ReferenceValuePair(
            reference = map(0),
            value = map(1)
        )

        internal fun <T: Any, DO: Any> addReferenceValuePairsDefinition(definitions: PropertyDefinitions<DO>, getter: (DO) -> List<ReferenceValuePair<T>>?) =
            definitions.add(0, "referenceValuePairs",
                ListDefinition(
                    valueDefinition = SubModelDefinition(
                        dataModel = {
                            @Suppress("UNCHECKED_CAST")
                            ReferenceValuePair as SimpleDataModel<ReferenceValuePair<T>, PropertyDefinitions<ReferenceValuePair<T>>>
                        }
                    )
                ),
                getter = getter
            )
    }
}

/** Convenience infix method to create Reference [value] pairs */
infix fun <T: Any> IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>.with(value: T) =
    ReferenceValuePair(this, value)
