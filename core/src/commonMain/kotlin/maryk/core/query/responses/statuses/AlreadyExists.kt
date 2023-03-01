package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.StatusType.ALREADY_EXISTS
import maryk.core.values.SimpleObjectValues

/** Given object with [key] already exists */
data class AlreadyExists<DM : IsRootModel>(
    val key: Key<DM>
) : IsAddResponseStatus<DM> {
    override val statusType = ALREADY_EXISTS

    internal companion object : SimpleQueryModel<AlreadyExists<*>>() {
        val key by addKey(AlreadyExists<*>::key)

        override fun invoke(values: SimpleObjectValues<AlreadyExists<*>>) =
            AlreadyExists<IsRootModel>(
                key = values(1u)
            )
    }
}
