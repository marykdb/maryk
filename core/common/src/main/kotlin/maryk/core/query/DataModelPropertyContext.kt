package maryk.core.query

import maryk.core.models.ObjectDataModel
import maryk.core.models.RootObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/**
 * Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
internal class DataModelPropertyContext(
    val dataModels: Map<String, () -> ObjectDataModel<*, *>>,
    override var dataModel: RootObjectDataModel<Any, ObjectPropertyDefinitions<Any>>? = null,
    var reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>>? = null
) : ContainsDataModelContext<RootObjectDataModel<Any, ObjectPropertyDefinitions<Any>>>
