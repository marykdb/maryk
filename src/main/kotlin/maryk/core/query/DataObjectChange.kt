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
import maryk.core.query.changes.ListPropertyChange
import maryk.core.query.changes.MapPropertyChange
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.changes.PropertyCheck
import maryk.core.query.changes.PropertyDelete
import maryk.core.query.changes.PropertyValueChange
import maryk.core.query.changes.SetPropertyChange

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
                                        dataModel = PropertyCheck
                                ),
                                2 to SubModelDefinition(
                                        required = true,
                                        dataModel = PropertyDelete
                                ),
                                3 to SubModelDefinition(
                                        required = true,
                                        dataModel = ObjectSoftDeleteChange
                                ),
                                4 to SubModelDefinition(
                                        required = true,
                                        dataModel = ListPropertyChange
                                ),
                                5 to SubModelDefinition(
                                        required = true,
                                        dataModel = SetPropertyChange
                                ),
                                6 to SubModelDefinition(
                                        required = true,
                                        dataModel = MapPropertyChange
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
