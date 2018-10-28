package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.query.responses.IsResponse

/** Defines a Get by keys request. */
interface IsGetRequest<DM: IsRootDataModel<*>, RP: IsResponse> : IsFetchRequest<DM, RP> {
    val keys: List<Key<DM>>

    companion object {
        internal fun <REQ: IsGetRequest<*, *>> addKeys(definitions: ObjectPropertyDefinitions<REQ>, getter: (REQ) -> List<Key<*>>?) =
            definitions.add(2, "keys", ListDefinition(
                valueDefinition = ContextualReferenceDefinition<RequestContext>(
                    contextualResolver = {
                        it?.dataModel as IsRootDataModel<*>? ?: throw ContextNotFoundException()
                    }
                )
            ), getter)
    }
}
