package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.RootObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.DataModelPropertyContext

/** Defines a Get by keys request. */
interface IsGetRequest<DO: Any, out DM: RootObjectDataModel<*, DO, *>> : IsFetchRequest<DO, DM> {
    val keys: List<Key<DO>>

    companion object {
        internal fun <DM: Any> addKeys(definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> List<Key<Any>>?) {
            definitions.add(1, "keys", ListDefinition(
                valueDefinition = ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = {
                        it?.dataModel ?: throw ContextNotFoundException()
                    }
                )
            ), getter)
        }
    }
}
