package maryk.core.query

import maryk.core.models.DataModel
import maryk.core.models.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
internal class DataModelPropertyContext(
    val dataModels: Map<String, () -> DataModel<*, *>>,
    override var dataModel: RootDataModel<Any, PropertyDefinitions<Any>>? = null,
    var reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>>? = null
) : ContainsDataModelContext<RootDataModel<Any, PropertyDefinitions<Any>>>
