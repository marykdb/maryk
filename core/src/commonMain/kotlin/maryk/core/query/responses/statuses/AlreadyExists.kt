package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.responses.statuses.StatusType.ALREADY_EXISTS
import maryk.core.values.SimpleObjectValues

/** Given object with [key] already exists */
data class AlreadyExists<DM : IsRootDataModel<*>>(
    val key: Key<DM>
) : IsAddResponseStatus<DM> {
    override val statusType = ALREADY_EXISTS

    @Suppress("unused")
    internal companion object : SimpleQueryDataModel<AlreadyExists<*>>(
        properties = object : ObjectPropertyDefinitions<AlreadyExists<*>>() {
            val key by addKey(AlreadyExists<*>::key)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AlreadyExists<*>>) =
            AlreadyExists<IsRootDataModel<IsValuesPropertyDefinitions>>(
                key = values(1u)
            )
    }
}
