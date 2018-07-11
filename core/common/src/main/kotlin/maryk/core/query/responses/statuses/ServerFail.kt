package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition

/** Something went wrong with the server with [reason] */
data class ServerFail<DM: IsRootDataModel<*>>(
    val reason: String
) : IsAddResponseStatus<DM>, IsChangeResponseStatus<DM>, IsDeleteResponseStatus<DM> {
    override val statusType = StatusType.SERVER_FAIL

    internal companion object: SimpleQueryDataModel<ServerFail<*>>(
        properties = object: ObjectPropertyDefinitions<ServerFail<*>>() {
            init {
                add(0, "reason", StringDefinition(), ServerFail<*>::reason)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<ServerFail<*>>) = ServerFail<IsRootDataModel<IsPropertyDefinitions>>(
            map(0)
        )
    }
}
