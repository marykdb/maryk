package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/**
 * Contains versioned [changes] for a specific DataObject by [key]
 */
data class DataObjectVersionedChange<out DO: Any>(
    val key: Key<DO>,
    val changes: List<VersionedChanges>
) {
    internal companion object: QueryDataModel<DataObjectVersionedChange<*>>(
        properties = object : PropertyDefinitions<DataObjectVersionedChange<*>>() {
            init {
                add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.key ?: throw ContextNotFoundException()
                    }
                ), DataObjectVersionedChange<*>::key)

                add(1, "changes", ListDefinition(
                    valueDefinition = SubModelDefinition(
                        dataModel = { VersionedChanges }
                    )
                ), DataObjectVersionedChange<*>::changes)
            }
        }
    ) {
        @Suppress("RemoveExplicitTypeArguments")
        override fun invoke(map: Map<Int, *>) = DataObjectVersionedChange(
            key = map(0),
            changes = map<List<VersionedChanges>?>(1) ?: emptyList()
        )
    }
}
