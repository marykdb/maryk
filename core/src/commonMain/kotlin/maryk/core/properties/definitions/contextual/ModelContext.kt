package maryk.core.properties.definitions.contextual

import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.query.ContainsDefinitionsContext

/**
 * Context to contain a reference to a model
 */
class ModelContext(
    val definitionsContext: ContainsDefinitionsContext?,
    var model: (() -> IsDataModel)? = null
) : IsPropertyContext
