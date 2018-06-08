package maryk.core.properties.definitions.contextual

import maryk.core.objects.AbstractDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelContext

/**
 * Context to contain a reference to a model
 */
class ModelContext(
    val dataModelContext: DataModelContext?
) : IsPropertyContext {
    var model: (() -> AbstractDataModel<Any, PropertyDefinitions<Any>, IsPropertyContext, IsPropertyContext>)? = null
}
