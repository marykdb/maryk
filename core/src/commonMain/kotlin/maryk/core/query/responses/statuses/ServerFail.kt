package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
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
                add(1, "reason", StringDefinition(), ServerFail<*>::reason)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ServerFail<*>>) = ServerFail<IsRootDataModel<IsPropertyDefinitions>>(
            values(1)
        )
    }
}
