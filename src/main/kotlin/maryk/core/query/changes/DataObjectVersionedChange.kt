package maryk.core.query.changes

import maryk.core.objects.Def
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
    internal object Properties : PropertyDefinitions<DataObjectVersionedChange<*>>() {
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
            definitions = listOf(
                    Def(Properties.key, DataObjectVersionedChange<*>::key),
                    Def(Properties.changes, DataObjectVersionedChange<*>::changes)
            ),
            properties = object : PropertyDefinitions<DataObjectVersionedChange<*>>() {
                init {
                    add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                            contextualResolver = { it!!.dataModel!!.key }
                    ), DataObjectVersionedChange<*>::key)

                    add(1, "changes", ListDefinition(
                            required = true,
                            valueDefinition = SubModelDefinition(
                                    required = true,
                                    dataModel = VersionedChanges
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
