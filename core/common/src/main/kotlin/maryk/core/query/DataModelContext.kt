package maryk.core.query

import maryk.core.models.IsNamedDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.enum.IndexedEnumDefinition

/**
 * Saves the context while writing and parsing Definitions
 * Context does not need to be cached since it is present in all phases.
 */
open class DataModelContext(
    internal val dataModels: MutableMap<String, () -> IsNamedDataModel<*>> = mutableMapOf(),
    internal val enums: MutableMap<String, IndexedEnumDefinition<*>> = mutableMapOf(),
    internal var propertyDefinitions: IsPropertyDefinitions? = null
) : IsPropertyContext
