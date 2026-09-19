package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.string
import maryk.core.query.responses.statuses.StatusType.SERVER_FAIL
import maryk.core.values.SimpleObjectValues

/** Something went wrong with the server with [reason] */
data class ServerFail<DM : IsRootDataModel>(
    val reason: String,
    val cause: Throwable? = null // Not communicated to other servers
) : IsAddResponseStatus<DM>, IsChangeResponseStatus<DM>, IsDeleteResponseStatus<DM> {
    override val statusType = SERVER_FAIL

    internal companion object : SimpleQueryModel<ServerFail<*>>() {
        val reason by string(
            1u, ServerFail<*>::reason
        )

        override fun invoke(values: SimpleObjectValues<ServerFail<*>>) =
            ServerFail<IsRootDataModel>(
                reason = values(reason.index)
            )
    }
}
