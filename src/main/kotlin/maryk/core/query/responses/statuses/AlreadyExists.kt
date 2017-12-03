package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.Key

/** Given object already exists
 * @param key of already existing object
 */
data class AlreadyExists<DO: Any>(
        val key: Key<DO>
) : IsAddResponseStatus<DO> {
    override val statusType = StatusType.ALREADY_EXISTS

    companion object: QueryDataModel<AlreadyExists<*>>(
            definitions = listOf(
                    Def(keyDefinition, AlreadyExists<*>::key)
            ),
            properties = object : PropertyDefinitions<AlreadyExists<*>>() {
                init {
                    add(0, "key", keyDefinition, AlreadyExists<*>::key)
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = AlreadyExists(
                key = map[0] as Key<Any>
        )
    }
}