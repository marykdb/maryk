package maryk.core.objects

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext

/** DataModel to contain query actions so they can be validated and transported
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 */
abstract class QueryDataModel<DO: Any>(
        properties: PropertyDefinitions<DO>
) : DataModel<DO, PropertyDefinitions<DO>, DataModelPropertyContext>(properties)