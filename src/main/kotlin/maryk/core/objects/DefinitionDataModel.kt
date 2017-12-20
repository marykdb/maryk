package maryk.core.objects

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelContext

/** DataModel to contain definitions
 * @param properties: All definitions for properties contained in this model
 * @param DO: Type of DataObject contained
 */
abstract class DefinitionDataModel<DO: Any>(
        properties: PropertyDefinitions<DO>
) : AbstractDataModel<DO, PropertyDefinitions<DO>, DataModelContext, DataModelContext>(properties)