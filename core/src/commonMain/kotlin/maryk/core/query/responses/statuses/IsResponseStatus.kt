package maryk.core.query.responses.statuses

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext

/** Response status */
interface IsResponseStatus {
    val statusType: StatusType
}

internal fun <DO : Any> ObjectPropertyDefinitions<DO>.addKey(getter: (DO) -> Key<*>?) =
    this.contextual(
        index = 1u,
        getter = getter,
        definition = ContextualReferenceDefinition<RequestContext>(
            contextualResolver = {
                it?.dataModel?.Model as IsRootDataModel<*>? ?: throw ContextNotFoundException()
            }
        )
    )
