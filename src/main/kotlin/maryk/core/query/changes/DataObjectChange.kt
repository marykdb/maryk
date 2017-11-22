package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.query.DataModelPropertyContext

/** Contains changes for a specific DataObject by key
 * @param key of DataObject to change
 */
data class DataObjectChange<out DO: Any>(
        val key: Key<out DO>,
        val changes: List<IsChange>,
        val lastVersion: UInt64? = null
) {
    constructor(
            key: Key<out DO>,
            vararg change: IsChange,
            lastVersion: UInt64? = null
    ) : this(key, change.toList(), lastVersion)

    object Properties {
        val key = ContextualReferenceDefinition<DataModelPropertyContext>(
                name = "key",
                index = 0,
                contextualResolver = { it!!.dataModel!!.key }
        )
        val changes = ListDefinition(
                name = "changes",
                index = 1,
                required = true,
                valueDefinition = MultiTypeDefinition(
                        required = true,
                        getDefinition = mapOfChangeDefinitions::get
                )
        )
        val lastVersion = NumberDefinition(
                name = "lastVersion",
                index = 2,
                type = UInt64
        )
    }

    companion object: QueryDataModel<DataObjectChange<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                DataObjectChange(
                        key = it[0] as Key<Any>,
                        changes = (it[1] as List<TypedValue<IsChange>>?)?.map { it.value } ?: emptyList(),
                        lastVersion = it[2] as UInt64?
                )
            },
            definitions = listOf(
                    Def(Properties.key, DataObjectChange<*>::key),
                    Def(Properties.changes, { it.changes.map { TypedValue(it.changeType.index, it) } }),
                    Def(Properties.lastVersion, DataObjectChange<*>::lastVersion)
            )
    )
}
