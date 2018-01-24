package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.numeric.UInt64

/** Action was completed successfully with [version] */
data class Success<DO: Any>(
    val version: UInt64
) : IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.SUCCESS

    internal companion object: QueryDataModel<Success<*>>(
        properties = object : PropertyDefinitions<Success<*>>() {
            init {
                add(0, "version", NumberDefinition(type = UInt64), Success<*>::version)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = Success<Any>(
            version = map[0] as UInt64
        )
    }
}