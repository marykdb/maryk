package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel

/** Something was wrong with the request
 * @param reason of failure
 */
data class RequestFail<DO: Any>(
        val reason: String
) : IsChangeResponseStatus<DO> {
    override val statusType = StatusType.REQUEST_FAIL

    companion object: QueryDataModel<RequestFail<*>>(
            construct = {
                RequestFail<Any>(it[0] as String)
            },
            definitions = listOf(
                    Def(reasonDefinition, RequestFail<*>::reason)
            )
    )
}