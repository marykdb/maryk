package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.StatusType.ALREADY_EXISTS
import maryk.core.values.SimpleObjectValues

/** Given object with [key] already exists */
data class AlreadyExists<DM : IsRootDataModel>(
    val key: Key<DM>
) : IsAddResponseStatus<DM> {
    override val statusType = ALREADY_EXISTS

    internal companion object : SimpleQueryModel<AlreadyExists<*>>() {
        val key by addKey(AlreadyExists<*>::key)

        override fun invoke(values: SimpleObjectValues<AlreadyExists<*>>) =
            AlreadyExists<IsRootDataModel>(
                key = values(1u)
            )
    }
}
