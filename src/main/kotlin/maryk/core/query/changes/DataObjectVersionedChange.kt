package maryk.core.query.changes

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
    companion object: QueryDataModel<DataObjectVersionedChange<*>>(
            properties = object : PropertyDefinitions<DataObjectVersionedChange<*>>() {
                init {
                    add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                            contextualResolver = { it!!.dataModel!!.key }
                    ), DataObjectVersionedChange<*>::key)

                    add(1, "changes", ListDefinition(
                            valueDefinition = SubModelDefinition(
                                    dataModel = { VersionedChanges }
                            )
                    ), DataObjectVersionedChange<*>::changes)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = DataObjectVersionedChange(
                key = map[0] as Key<Any>,
                changes = (map[1] as List<VersionedChanges>?) ?: emptyList()
        )
    }
}
