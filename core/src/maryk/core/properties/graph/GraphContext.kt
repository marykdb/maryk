package maryk.core.properties.graph

import maryk.core.models.IsDataModel
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ContainsDataModelContext

/** Context for Graph serializing */
class GraphContext(
    override var dataModel: IsDataModel? = null,
    var subDataModel: IsDataModel? = null,
    var reference: IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>? = null,
) : ContainsDataModelContext<IsDataModel>
