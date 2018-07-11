package maryk.core.query.responses.statuses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key

/** Response that object of [key] does not exist */
data class DoesNotExist<DO: Any>(
    val key: Key<DO>
) : IsChangeResponseStatus<DO>, IsDeleteResponseStatus<DO> {
    override val statusType = StatusType.DOES_NOT_EXIST

    internal companion object: SimpleQueryDataModel<DoesNotExist<*>>(
        properties = object : ObjectPropertyDefinitions<DoesNotExist<*>>() {
            init {
                IsResponseStatus.addKey(this, DoesNotExist<*>::key)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<DoesNotExist<*>>) = DoesNotExist(
            key = map(0)
        )
    }
}
