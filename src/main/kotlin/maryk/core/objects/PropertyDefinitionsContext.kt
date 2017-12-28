package maryk.core.objects

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions

/** Context to hold PropertyDefinitions for resolving transported DataModels */
class PropertyDefinitionsContext(
        var propertyDefinitions: PropertyDefinitions<*>? = null
) : IsPropertyContext