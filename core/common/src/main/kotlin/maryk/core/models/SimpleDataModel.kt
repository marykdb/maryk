package maryk.core.models

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.PropertyDefinitions

/**
 * DataModel of type [DO] for non contextual models.
 * Contains [properties] definitions of type [P]
 */
abstract class SimpleDataModel<DO: Any, P: PropertyDefinitions<DO>> internal constructor(
    properties: P
) : AbstractDataModel<DO, P, IsPropertyContext, IsPropertyContext>(
    properties
)
