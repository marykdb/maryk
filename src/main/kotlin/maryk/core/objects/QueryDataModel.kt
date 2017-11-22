package maryk.core.objects

import maryk.core.query.properties.DataModelPropertyContext

/** DataModel to contain query actions so they can be validated and transported
 * @param construct: Constructs object out of a map with values keyed on index.
 * @param definitions: All definitions for properties contained in this model
 * @param DM: Type of DataModel contained
 */
open class QueryDataModel<DM: Any>(
        construct: (Map<Int, *>) -> DM,
        definitions: List<Def<*, DM, DataModelPropertyContext>>
) : DataModel<DM, DataModelPropertyContext>(construct, definitions)