package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
class RequestContext(
    val definitionsContext: ContainsDefinitionsContext,
    override var dataModel: IsDataModel<*>? = null,
    var reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>, *>? = null
) : ContainsDataModelContext<IsDataModel<*>>, ContainsDefinitionsContext by definitionsContext {
    /** For test use */
    internal constructor(
        dataModels: Map<String, () -> IsNamedDataModel<*>>,
        dataModel: IsDataModel<*>? = null,
        reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>, *>? = null
    ) : this(
        DefinitionsContext(dataModels.toMutableMap()),
        dataModel,
        reference
    )
}
