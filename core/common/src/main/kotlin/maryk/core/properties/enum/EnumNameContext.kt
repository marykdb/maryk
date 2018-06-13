package maryk.core.properties.enum

import maryk.core.properties.IsPropertyContext
import maryk.core.query.DataModelContext

/** Context to store Enum name for later reference. */
class EnumNameContext(
    val dataModelContext: DataModelContext? = null
): IsPropertyContext {
    var name: String? = null
    var isOriginalDefinition: Boolean? = false
}
