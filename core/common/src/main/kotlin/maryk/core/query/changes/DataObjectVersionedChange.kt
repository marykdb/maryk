package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.ObjectPropertyDefinitions
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
    internal companion object: SimpleQueryDataModel<DataObjectVersionedChange<*>>(
        properties = object : ObjectPropertyDefinitions<DataObjectVersionedChange<*>>() {
            init {
                add(0, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel?.key ?: throw ContextNotFoundException()
                    }
                ), DataObjectVersionedChange<*>::key)

                add(1, "changes", ListDefinition(
                    default = emptyList(),
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { VersionedChanges }
                    )
                ), DataObjectVersionedChange<*>::changes)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<DataObjectVersionedChange<*>>) = DataObjectVersionedChange(
            key = map(0),
            changes = map(1)
        )
    }
}
