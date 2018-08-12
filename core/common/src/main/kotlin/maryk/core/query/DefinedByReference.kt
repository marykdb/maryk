package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/**
 * For objects containing a reference which defines the context of other properties
 */
interface DefinedByReference<T: Any> {
    val reference: IsPropertyReference<T, IsPropertyDefinition<T>, *>

    companion object {
        internal fun <DO: Any> addReference(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> AnyPropertyReference?) =
            definitions.add(
                index = 1, name = "reference",
                definition = ContextualPropertyReferenceDefinition<RequestContext>(
                    contextualResolver = {
                        it?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.reference = value as IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>, *>
                }
            )
    }
}
