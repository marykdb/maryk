package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.string
import maryk.core.query.responses.statuses.StatusType.REQUEST_FAIL
import maryk.core.values.SimpleObjectValues

/**
 * Something was wrong with [reason] with the request
 */
data class RequestFail<DM : IsRootModel>(
    val reason: String
) : IsChangeResponseStatus<DM> {
    override val statusType = REQUEST_FAIL

    @Suppress("unused")
    internal companion object : SimpleQueryModel<RequestFail<*>>() {
        val reason by string(1u, RequestFail<*>::reason)

        override fun invoke(values: SimpleObjectValues<RequestFail<*>>) =
            RequestFail<IsRootModel>(
                values(1u)
            )
    }
}
