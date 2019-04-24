package maryk.core.query

import maryk.core.models.IsDataModel
import maryk.core.properties.IsPropertyContext

interface ContainsDataModelContext<out DM : IsDataModel<*>> : IsPropertyContext {
    val dataModel: DM?
}
