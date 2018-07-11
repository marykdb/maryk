package maryk.core.query.responses.statuses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.Key

/** Given object with [key] already exists */
data class AlreadyExists<DO: Any>(
    val key: Key<DO>
) : IsAddResponseStatus<DO> {
    override val statusType = StatusType.ALREADY_EXISTS

    internal companion object: SimpleQueryDataModel<AlreadyExists<*>>(
        properties = object : ObjectPropertyDefinitions<AlreadyExists<*>>() {
            init {
                IsResponseStatus.addKey(this, AlreadyExists<*>::key)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<AlreadyExists<*>>) = AlreadyExists(
            key = map(0)
        )
    }
}
