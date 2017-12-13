package maryk.core.query

import maryk.core.objects.RootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference

/** Saves the context while writing and parsing Requests and Responses
 * Context does not need to be cached since it is present in all phases.
 */
class DataModelPropertyContext(
        val dataModels: Map<String, RootDataModel<*, *>>,
        internal var dataModel: RootDataModel<Any, PropertyDefinitions<Any>>? = null,
        internal var reference: IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *>>? = null
) : IsPropertyContext