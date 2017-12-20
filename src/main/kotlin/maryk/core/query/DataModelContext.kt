package maryk.core.query

import maryk.core.objects.DataModel
import maryk.core.properties.IsPropertyContext

/** Saves the context while writing and parsing Definitions
 * Context does not need to be cached since it is present in all phases.
 */
open class DataModelContext : IsPropertyContext {
    val dataModels: MutableMap<String, DataModel<*, *>> = mutableMapOf()
}