package maryk.core.properties.graph

import maryk.core.models.AbstractDataModel
import maryk.core.query.ContainsDataModelContext

/** Context for Graph serializing */
class GraphContext(
    override var dataModel: AbstractDataModel<*, *, *, *>? = null,
    var subDataModel: AbstractDataModel<*, *, *, *>? = null
) : ContainsDataModelContext<AbstractDataModel<*, *, *, *>>
