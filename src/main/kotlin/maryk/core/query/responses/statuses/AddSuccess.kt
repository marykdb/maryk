package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.mapOfChangeDefinitions

/** Successful add of given object
 * @param key of persisted object
 * @param version of the add
 * @param changes list of changes added by server to object
 */
data class AddSuccess<DO: Any>(
        val key: Key<DO>,
        val version: UInt64,
        val changes: List<IsChange>
) : IsAddResponseStatus<DO> {
    override val statusType = StatusType.ADD_SUCCESS

    companion object: QueryDataModel<AddSuccess<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                AddSuccess(
                        key = it[0] as Key<Any>,
                        version = it[1] as UInt64,
                        changes = (it[2] as List<TypedValue<IsChange>>?)?.map { it.value } ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(keyDefinition, AddSuccess<*>::key),
                    Def(
                            NumberDefinition("version", 1, type = UInt64),
                            AddSuccess<*>::version
                    ),
                    Def(
                            ListDefinition(
                                    name = "changes",
                                    index = 2,
                                    required = true,
                                    valueDefinition = MultiTypeDefinition(
                                            required = true,
                                            getDefinition = mapOfChangeDefinitions::get
                                    )
                            ),
                            { it.changes.map { TypedValue(it.changeType.index, it) } }
                    )
            )
    )
}