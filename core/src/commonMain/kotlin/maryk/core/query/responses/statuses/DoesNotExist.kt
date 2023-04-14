package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.StatusType.DOES_NOT_EXIST
import maryk.core.values.SimpleObjectValues

/** Response that object of [key] does not exist */
data class DoesNotExist<DM : IsRootDataModel>(
    val key: Key<DM>
) : IsChangeResponseStatus<DM>, IsDeleteResponseStatus<DM> {
    override val statusType = DOES_NOT_EXIST

    internal companion object : SimpleQueryModel<DoesNotExist<*>>() {
        val key by addKey(DoesNotExist<*>::key)

        override fun invoke(values: SimpleObjectValues<DoesNotExist<*>>) =
            DoesNotExist<IsRootDataModel>(
                key = values(key.index)
            )
    }
}
