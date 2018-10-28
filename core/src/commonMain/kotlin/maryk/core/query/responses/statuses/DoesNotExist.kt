package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key

/** Response that object of [key] does not exist */
data class DoesNotExist<DM: IsRootDataModel<*>>(
    val key: Key<DM>
) : IsChangeResponseStatus<DM>, IsDeleteResponseStatus<DM> {
    override val statusType = StatusType.DOES_NOT_EXIST

    internal companion object: SimpleQueryDataModel<DoesNotExist<*>>(
        properties = object : ObjectPropertyDefinitions<DoesNotExist<*>>() {
            init {
                IsResponseStatus.addKey(this, DoesNotExist<*>::key)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<DoesNotExist<*>>) = DoesNotExist<IsRootDataModel<IsPropertyDefinitions>>(
            key = map(1)
        )
    }
}
