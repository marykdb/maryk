package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext

internal interface ContainsDataModelContext<DM: IsDataModel<*>>: IsPropertyContext {
    var dataModel: DM?
}
