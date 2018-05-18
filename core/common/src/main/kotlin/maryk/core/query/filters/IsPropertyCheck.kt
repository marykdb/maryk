package maryk.core.query.filters

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsValuePropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Check of property of type [T] */
interface IsPropertyCheck<T: Any> : IsFilter {
    val reference: IsPropertyReference<T, IsValuePropertyDefinitionWrapper<T, *, IsPropertyContext, *>>

    companion object {
        internal fun <DO: Any> addReference(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsPropertyReference<*, *>?) =
            definitions.add(
                index = 0, name = "reference",
                definition = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.properties ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.reference = value as IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>>
                }
            )
    }
}
