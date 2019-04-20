package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.values.SimpleObjectValues

/** Given object with [key] already exists */
data class AlreadyExists<DM : IsRootDataModel<*>>(
    val key: Key<DM>
) : IsAddResponseStatus<DM> {
    override val statusType = StatusType.ALREADY_EXISTS

    internal companion object : SimpleQueryDataModel<AlreadyExists<*>>(
        properties = object : ObjectPropertyDefinitions<AlreadyExists<*>>() {
            init {
                IsResponseStatus.addKey(this, AlreadyExists<*>::key)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AlreadyExists<*>>) =
            AlreadyExists<IsRootDataModel<IsPropertyDefinitions>>(
                key = values(1u)
            )
    }
}
