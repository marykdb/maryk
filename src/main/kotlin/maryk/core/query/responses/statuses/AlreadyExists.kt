package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.types.Key

/** Given object already exists
 * @param key of already existing object
 */
data class AlreadyExists<DO: Any>(
        val key: Key<DO>
) : IsAddResponseStatus<DO> {
    override val statusType = StatusType.ALREADY_EXISTS

    companion object: QueryDataModel<AlreadyExists<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                AlreadyExists(
                        key = it[0] as Key<Any>
                )
            },
            definitions = listOf(
                    Def(keyDefinition, AlreadyExists<*>::key)
            )
    )
}