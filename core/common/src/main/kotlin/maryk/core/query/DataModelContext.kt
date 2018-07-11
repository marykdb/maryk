package maryk.core.query

import maryk.core.models.ObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.enum.IndexedEnumDefinition

/**
 * Saves the context while writing and parsing Definitions
 * Context does not need to be cached since it is present in all phases.
 */
open class DataModelContext(
    internal val dataModels: MutableMap<String, () -> ObjectDataModel<*, *>> = mutableMapOf(),
    internal val enums: MutableMap<String, IndexedEnumDefinition<*>> = mutableMapOf(),
    internal var propertyDefinitions: ObjectPropertyDefinitions<*>? = null
) : IsPropertyContext
