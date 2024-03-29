package maryk.core.query

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.BaseDataModel
import maryk.core.models.IsObjectDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference

/**
 * For objects containing a reference which defines the context of other properties
 */
interface DefinedByReference<out T : Any> {
    val reference: IsPropertyReference<out T, IsPropertyDefinition<out T>, *>
}

internal fun <DO : Any> IsObjectDataModel<DO>.addReference(
    getter: (DO) -> AnyPropertyReference?
) =
    this.contextual(
        index = 1u,
        definition = ContextualPropertyReferenceDefinition<RequestContext>(
            contextualResolver = {
                it?.dataModel as? BaseDataModel<*>?
                    ?: throw ContextNotFoundException()
            }
        ),
        getter = getter,
        capturer = { context, value ->
            @Suppress("UNCHECKED_CAST")
            context.reference = value as IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>
        }
    )
