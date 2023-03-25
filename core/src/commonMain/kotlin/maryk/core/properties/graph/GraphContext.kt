package maryk.core.properties.graph

import maryk.core.properties.IsPropertyDefinitions
import maryk.core.query.ContainsDataModelContext

/** Context for Graph serializing */
class GraphContext(
    override var dataModel: IsPropertyDefinitions? = null,
    var subDataModel: IsPropertyDefinitions? = null
) : ContainsDataModelContext<IsPropertyDefinitions>
