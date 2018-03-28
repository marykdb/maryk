package maryk.core.query

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions

/**
 * Saves the context while writing and parsing Definitions
 * Context does not need to be cached since it is present in all phases.
 */
internal open class DataModelContext(
    internal val dataModels: MutableMap<String, DataModel<*, *>> = mutableMapOf(),
    internal var propertyDefinitions: PropertyDefinitions<*>? = null
) : IsPropertyContext