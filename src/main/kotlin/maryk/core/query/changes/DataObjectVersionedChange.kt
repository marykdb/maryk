package maryk.core.query.changes

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/** Contains versioned changes for a specific DataObject by key
 * @param key of DataObject to change
 * @param changes List of Versioned Changes
 */
data class DataObjectVersionedChange<out DO: Any>(
        val key: Key<DO>,
        val changes: List<VersionedChanges>
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
                valueDefinition = SubModelDefinition(
                        required = true,
                        dataModel = VersionedChanges
                )
        )
    }

    companion object: QueryDataModel<DataObjectVersionedChange<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                DataObjectVersionedChange(
                        key = it[0] as Key<Any>,
                        changes = (it[1] as List<VersionedChanges>?) ?: emptyList()
                )
            },
            definitions = listOf(
                    Def(Properties.key, DataObjectVersionedChange<*>::key),
                    Def(Properties.changes, DataObjectVersionedChange<*>::changes)
            )
    )
}
