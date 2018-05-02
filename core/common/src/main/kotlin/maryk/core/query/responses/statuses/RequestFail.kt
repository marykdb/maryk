package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

/**
 * Something was wrong with [reason] with the request
 */
data class RequestFail<DO: Any>(
    val reason: String
) : IsChangeResponseStatus<DO> {
    override val statusType = StatusType.REQUEST_FAIL

    internal companion object: QueryDataModel<RequestFail<*>>(
        properties = object : PropertyDefinitions<RequestFail<*>>() {
            init {
                add(0, "reason", StringDefinition(), RequestFail<*>::reason)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = RequestFail<Any>(map(0))
    }
}
