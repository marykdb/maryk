package maryk.core.properties.definitions.contextual

import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.query.DefinitionsContext

/**
 * Context to contain a reference to a model
 */
class ModelContext(
    val definitionsContext: DefinitionsContext?
) : IsPropertyContext {
    var model: (() -> IsDataModel<*>)? = null
}
