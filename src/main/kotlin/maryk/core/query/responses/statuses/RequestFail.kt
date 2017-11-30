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
            definitions = listOf(
                    Def(reasonDefinition, RequestFail<*>::reason)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = RequestFail<Any>(map[0] as String)
    }
}