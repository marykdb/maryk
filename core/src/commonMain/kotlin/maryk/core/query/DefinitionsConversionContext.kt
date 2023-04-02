package maryk.core.query

import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsDataModel

/**
 * Saves the context while writing and parsing Definitions
 * Stores the properties of in process models so other values can be based on it
 */
open class DefinitionsConversionContext(
    internal val definitionsContext: DefinitionsContext = DefinitionsContext(),

    // Used to resolve keys in root model
    internal var propertyDefinitions: IsDataModel? = null
) : IsPropertyContext, ContainsDefinitionsContext by definitionsContext
