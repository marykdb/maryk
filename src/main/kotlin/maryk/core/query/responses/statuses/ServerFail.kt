package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

/** Something went wrong with the server
 * @param reason of failure
 */
data class ServerFail<DO: Any>(
        val reason: String
) : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.SERVER_FAIL

    companion object: QueryDataModel<ServerFail<*>>(
            properties = object: PropertyDefinitions<ServerFail<*>>() {
                init {
                    add(0, "reason", StringDefinition(), ServerFail<*>::reason)
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = ServerFail<Any>(map[0] as String)
    }
}