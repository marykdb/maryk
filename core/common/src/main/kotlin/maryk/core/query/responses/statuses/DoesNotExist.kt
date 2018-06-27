package maryk.core.query.responses.statuses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key

/** Response that object of [key] does not exist */
data class DoesNotExist<DO: Any>(
    val key: Key<DO>
) : IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.DOES_NOT_EXIST

    internal companion object: SimpleQueryDataModel<DoesNotExist<*>>(
        properties = object : PropertyDefinitions<DoesNotExist<*>>() {
            init {
                IsResponseStatus.addKey(this, DoesNotExist<*>::key)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = DoesNotExist(
            key = map(0)
        )
    }
}
