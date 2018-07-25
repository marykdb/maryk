package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key

/** Given object with [key] already exists */
data class AlreadyExists<DM: IsRootDataModel<*>>(
    val key: Key<*>
) : IsAddResponseStatus<DM> {
    override val statusType = StatusType.ALREADY_EXISTS

    internal companion object: SimpleQueryDataModel<AlreadyExists<*>>(
        properties = object : ObjectPropertyDefinitions<AlreadyExists<*>>() {
            init {
                IsResponseStatus.addKey(this, AlreadyExists<*>::key)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<AlreadyExists<*>>) = AlreadyExists<IsRootDataModel<IsPropertyDefinitions>>(
            key = map(1)
        )
    }
}
