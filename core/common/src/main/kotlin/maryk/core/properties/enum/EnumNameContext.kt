package maryk.core.properties.enum

import maryk.core.properties.IsPropertyContext
import maryk.core.query.DefinitionsContext

/** Context to store Enum name for later reference. */
class EnumNameContext(
    val definitionsContext: DefinitionsContext? = null
): IsPropertyContext {
    var name: String? = null
    var isOriginalDefinition: Boolean? = false
}
