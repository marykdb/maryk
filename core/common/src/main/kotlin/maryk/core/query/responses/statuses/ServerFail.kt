package maryk.core.query.responses.statuses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

/** Something went wrong with the server with [reason] */
data class ServerFail<DO: Any>(
    val reason: String
) : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.SERVER_FAIL

    internal companion object: SimpleQueryDataModel<ServerFail<*>>(
        properties = object: PropertyDefinitions<ServerFail<*>>() {
            init {
                add(0, "reason", StringDefinition(), ServerFail<*>::reason)
            }
        }
    ) {
        override fun invoke(map: ValueMap<ServerFail<*>>) = ServerFail<Any>(
            map[0] as String
        )
    }
}
