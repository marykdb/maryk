package maryk.core.objects

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.query.DataModelPropertyContext

/**
 * DataModel of type [DO] with [properties] definitions to contain
 * query actions so they can be validated and transported
 */
internal abstract class QueryDataModel<DO: Any>(
    properties: PropertyDefinitions<DO>
) : AbstractDataModel<DO, PropertyDefinitions<DO>, DataModelPropertyContext, DataModelPropertyContext>(properties)