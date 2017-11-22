package maryk.core.query

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.UInt64
import maryk.core.query.changes.IsChange
import maryk.core.query.changes.PropertyDelete
import maryk.core.query.changes.PropertyValueChange

/** Contains changes for a specific DataObject by key
 * @param key of DataObject to change
 */
class DataObjectChange<out DO: Any>(
        val key: Key<out DO>,
        vararg val changes: TypedValue<IsChange>,
        val lastVersion: UInt64? = null
) {
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
                        typeMap = mapOf(
                                0 to SubModelDefinition(
                                        required = true,
                                        dataModel = PropertyValueChange
                                ),
                                1 to SubModelDefinition(
                                        required = true,
                                        dataModel = PropertyDelete
                                )
                        )
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
                        changes = *(it[1] as List<TypedValue<IsChange>>).toTypedArray(),
                        lastVersion = it[2] as UInt64?
                )
            },
            definitions = listOf(
                    Def(Properties.key, DataObjectChange<*>::key),
                    Def(Properties.changes, { it.changes.toList() }),
                    Def(Properties.lastVersion, DataObjectChange<*>::lastVersion)
            )
    )
}
