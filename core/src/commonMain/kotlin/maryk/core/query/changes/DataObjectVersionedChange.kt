package maryk.core.query.changes

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsRootModel
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/**
 * Contains versioned [changes] for a specific DataObject by [key]
 */
data class DataObjectVersionedChange<out DM : IsRootModel>(
    val key: Key<DM>,
    val sortingKey: Bytes? = null,
    val changes: List<VersionedChanges>
) {
    companion object : QueryModel<DataObjectVersionedChange<*>, Companion>() {
        val key by contextual(
            index = 1u,
            getter = DataObjectVersionedChange<*>::key,
            definition = ContextualReferenceDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                }
            )
        )

        val sortingKey by flexBytes(
            index = 2u,
            getter = DataObjectVersionedChange<*>::sortingKey,
            default = null,
            required = false
        )

        val changes by list(
            index = 3u,
            getter = DataObjectVersionedChange<*>::changes,
            default = emptyList(),
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { VersionedChanges.Model }
            )
        )

        override fun invoke(values: ObjectValues<DataObjectVersionedChange<*>, Companion>) = DataObjectVersionedChange<IsRootModel>(
            key = values(1u),
            sortingKey = values(2u),
            changes = values(3u)
        )
    }
}
