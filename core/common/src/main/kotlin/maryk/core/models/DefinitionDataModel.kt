package maryk.core.models

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelContext

/** DataModel of type [DO] to contain [properties] definitions */
abstract class DefinitionDataModel<DO: Any>(
    properties: ObjectPropertyDefinitions<DO>
) : AbstractDataModel<DO, ObjectPropertyDefinitions<DO>, DataModelContext, DataModelContext>(properties)
