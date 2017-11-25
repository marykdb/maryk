package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel

/** Something went wrong with the server
 * @param reason of failure
 */
data class ServerFail<DO: Any>(
        val reason: String
) : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.SERVER_FAIL

    companion object: QueryDataModel<ServerFail<*>>(
            construct = {
                ServerFail<Any>(it[0] as String)
            },
            definitions = listOf(
                    Def(reasonDefinition, ServerFail<*>::reason)
            )
    )
}