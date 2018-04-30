package maryk.core.objects

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelContext

/** DataModel of type [DO] to contain [properties] definitions */
abstract class DefinitionDataModel<DO: Any>(
    properties: PropertyDefinitions<DO>
) : AbstractDataModel<DO, PropertyDefinitions<DO>, DataModelContext, DataModelContext>(properties)
