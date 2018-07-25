package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/**
 * Contains versioned [changes] for a specific DataObject by [key]
 */
data class DataObjectVersionedChange<out DM: IsRootDataModel<*>>(
    val key: Key<DM>,
    val changes: List<VersionedChanges>
) {
    internal companion object: SimpleQueryDataModel<DataObjectVersionedChange<*>>(
        properties = object : ObjectPropertyDefinitions<DataObjectVersionedChange<*>>() {
            init {
                add(1, "key", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                    }
                ), DataObjectVersionedChange<*>::key)

                add(2, "changes", ListDefinition(
                    default = emptyList(),
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { VersionedChanges }
                    )
                ), DataObjectVersionedChange<*>::changes)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<DataObjectVersionedChange<*>>) = DataObjectVersionedChange(
            key = map(1),
            changes = map(2)
        )
    }
}
