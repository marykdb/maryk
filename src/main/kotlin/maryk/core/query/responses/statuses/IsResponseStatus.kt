package maryk.core.query.responses.statuses

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/** Response status */
interface IsResponseStatus {
    val statusType: StatusType

    companion object {
        internal fun <DM: Any> addKey(definitions: PropertyDefinitions<DM>, getter: (DM) -> Key<*>?) {
            definitions.add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = { it!!.dataModel!!.key }
            ), getter)
        }
    }
}