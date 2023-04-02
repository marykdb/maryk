package maryk.core.properties.graph

import maryk.core.models.IsDataModel
import maryk.core.query.ContainsDataModelContext

/** Context for Graph serializing */
class GraphContext(
    override var dataModel: IsDataModel? = null,
    var subDataModel: IsDataModel? = null
) : ContainsDataModelContext<IsDataModel>
