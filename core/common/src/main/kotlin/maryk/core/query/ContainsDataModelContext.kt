package maryk.core.query

import maryk.core.objects.AbstractDataModel
import maryk.core.properties.IsPropertyContext

internal interface ContainsDataModelContext<DM: AbstractDataModel<*, *, *, *>>: IsPropertyContext {
    var dataModel: DM?
}
