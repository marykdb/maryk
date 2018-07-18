package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.models.IsNamedDataModel
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
internal class DataModelPropertyContext(
    val dataModels: Map<String, () -> IsNamedDataModel<*>>,
    override var dataModel: IsDataModel<*>? = null,
    var reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>>? = null
) : ContainsDataModelContext<IsDataModel<*>>
