package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/**
 * For objects containing a reference which defines the context of other properties
 */
interface DefinedByReference<out T : Any> {
    val reference: IsPropertyReference<out T, IsPropertyDefinition<out T>, *>

    companion object {
        internal fun <DO : Any> addReference(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> AnyPropertyReference?
        ) =
            definitions.add(
                index = 1u, name = "reference",
                definition = ContextualPropertyReferenceDefinition<RequestContext>(
                    contextualResolver = {
                        it?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                            ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.reference = value as IsPropertyReference<*, IsChangeableValueDefinition<*, *>, *>
                }
            )
    }
}
