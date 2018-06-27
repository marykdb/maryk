package maryk.core.query.responses.statuses

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key

/** Given object with [key] already exists */
data class AlreadyExists<DO: Any>(
    val key: Key<DO>
) : IsAddResponseStatus<DO> {
    override val statusType = StatusType.ALREADY_EXISTS

    internal companion object: QueryDataModel<AlreadyExists<*>>(
        properties = object : PropertyDefinitions<AlreadyExists<*>>() {
            init {
                IsResponseStatus.addKey(this, AlreadyExists<*>::key)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = AlreadyExists(
            key = map(0)
        )
    }
}
