package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext

interface ContainsDataModelContext<DM: IsDataModel<*>>: IsPropertyContext {
    val dataModel: DM?
}
