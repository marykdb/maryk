package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.string
import maryk.core.query.responses.statuses.StatusType.SERVER_FAIL
import maryk.core.values.SimpleObjectValues

/** Something went wrong with the server with [reason] */
data class ServerFail<DM : IsRootModel>(
    val reason: String,
    val cause: Throwable? = null // Not communicated to other servers
) : IsAddResponseStatus<DM>, IsChangeResponseStatus<DM>, IsDeleteResponseStatus<DM> {
    override val statusType = SERVER_FAIL

    @Suppress("unused")
    internal companion object : SimpleQueryModel<ServerFail<*>>() {
        val reason by string(
            1u, ServerFail<*>::reason
        )

        override fun invoke(values: SimpleObjectValues<ServerFail<*>>) =
            ServerFail<IsRootModel>(
                reason = values(1u)
            )
    }
}
