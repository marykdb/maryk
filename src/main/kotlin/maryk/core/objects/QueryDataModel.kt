package maryk.core.objects

import maryk.core.query.DataModelPropertyContext

/** DataModel to contain query actions so they can be validated and transported
 * @param definitions: All definitions for properties contained in this model
 * @param DM: Type of DataModel contained
 */
abstract class QueryDataModel<DM: Any>(
        definitions: List<Def<*, DM, DataModelPropertyContext>>
) : DataModel<DM, DataModelPropertyContext>(definitions)