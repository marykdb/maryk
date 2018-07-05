package maryk.core.query.responses.statuses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.numeric.UInt64

/** Action was completed successfully with [version] */
data class Success<DO: Any>(
    val version: UInt64
) : IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.SUCCESS

    internal companion object: SimpleQueryDataModel<Success<*>>(
        properties = object : PropertyDefinitions<Success<*>>() {
            init {
                add(0, "version", NumberDefinition(type = UInt64), Success<*>::version)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<Success<*>>) = Success<Any>(
            version = map(0)
        )
    }
}
