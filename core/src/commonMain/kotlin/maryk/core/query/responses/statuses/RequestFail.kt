package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.values.SimpleObjectValues

/**
 * Something was wrong with [reason] with the request
 */
data class RequestFail<DM : IsRootDataModel<*>>(
    val reason: String
) : IsChangeResponseStatus<DM> {
    override val statusType = StatusType.REQUEST_FAIL

    internal companion object : SimpleQueryDataModel<RequestFail<*>>(
        properties = object : ObjectPropertyDefinitions<RequestFail<*>>() {
            init {
                add(1u, "reason", StringDefinition(), RequestFail<*>::reason)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<RequestFail<*>>) =
            RequestFail<IsRootDataModel<IsPropertyDefinitions>>(
                values(1u)
            )
    }
}
