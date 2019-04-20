package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt64
import maryk.core.values.SimpleObjectValues

/** Action was completed successfully with [version] */
data class Success<DM : IsRootDataModel<*>>(
    val version: ULong
) : IsChangeResponseStatus<DM>, IsDeleteResponseStatus<DM> {
    override val statusType = StatusType.SUCCESS

    internal companion object : SimpleQueryDataModel<Success<*>>(
        properties = object : ObjectPropertyDefinitions<Success<*>>() {
            init {
                add(1u, "version", NumberDefinition(type = UInt64), Success<*>::version)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<Success<*>>) = Success<IsRootDataModel<IsPropertyDefinitions>>(
            version = values(1u)
        )
    }
}
