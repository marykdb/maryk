package maryk.core.query.responses.statuses

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext

/** Response status */
interface IsResponseStatus {
    val statusType: StatusType

    companion object {
        internal fun <DO : Any> addKey(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> Key<*>?) {
            definitions.add(1, "key", ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            ), getter)
        }
    }
}
