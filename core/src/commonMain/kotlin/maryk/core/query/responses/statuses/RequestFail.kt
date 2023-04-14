package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.string
import maryk.core.query.responses.statuses.StatusType.REQUEST_FAIL
import maryk.core.values.SimpleObjectValues

/**
 * Something was wrong with [reason] with the request
 */
data class RequestFail<DM : IsRootDataModel>(
    val reason: String
) : IsChangeResponseStatus<DM> {
    override val statusType = REQUEST_FAIL

    internal companion object : SimpleQueryModel<RequestFail<*>>() {
        val reason by string(1u, RequestFail<*>::reason)

        override fun invoke(values: SimpleObjectValues<RequestFail<*>>) =
            RequestFail<IsRootDataModel>(
                values(reason.index)
            )
    }
}
