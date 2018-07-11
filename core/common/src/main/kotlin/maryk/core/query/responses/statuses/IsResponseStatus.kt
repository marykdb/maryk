package maryk.core.query.responses.statuses

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/** Response status */
interface IsResponseStatus {
    val statusType: StatusType

    companion object {
        internal fun <DO: Any> addKey(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> Key<*>?) {
            definitions.add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                contextualResolver = {
                    it?.dataModel ?: throw ContextNotFoundException()
                }
            ), getter)
        }
    }
}
