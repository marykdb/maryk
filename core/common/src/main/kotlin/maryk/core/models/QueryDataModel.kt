package maryk.core.models

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.query.DataModelPropertyContext

/**
 * DataModel of type [DO] with [properties] definitions to contain
 * query actions so they can be validated and transported
 */
internal typealias QueryDataModel<DO, P> = AbstractDataModel<DO, P, DataModelPropertyContext, DataModelPropertyContext>
internal typealias SimpleQueryDataModel<DO> = AbstractDataModel<DO, ObjectPropertyDefinitions<DO>, DataModelPropertyContext, DataModelPropertyContext>
