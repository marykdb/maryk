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
            definitions = listOf(
                    Def(reasonDefinition, ServerFail<*>::reason)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = ServerFail<Any>(map[0] as String)
    }
}