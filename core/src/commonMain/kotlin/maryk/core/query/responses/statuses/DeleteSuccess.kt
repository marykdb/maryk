package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.responses.statuses.StatusType.DELETE_SUCCESS
import maryk.core.values.SimpleObjectValues

/** Delete action was completed successfully with [version] */
data class DeleteSuccess<DM : IsRootDataModel<*>>(
    val version: ULong
) : IsDeleteResponseStatus<DM> {
    override val statusType = DELETE_SUCCESS

    internal companion object : SimpleQueryDataModel<DeleteSuccess<*>>(
        properties = object : ObjectPropertyDefinitions<DeleteSuccess<*>>() {
            val version by number(1u, DeleteSuccess<*>::version, type = UInt64)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DeleteSuccess<*>>) = DeleteSuccess<IsRootDataModel<IsPropertyDefinitions>>(
            version = values(1u)
        )
    }
}
