package maryk.core.query

import maryk.core.properties.IsPropertyContext
import maryk.core.models.IsDataModel

interface ContainsDataModelContext<out DM : IsDataModel> : IsPropertyContext {
    val dataModel: DM?
}
