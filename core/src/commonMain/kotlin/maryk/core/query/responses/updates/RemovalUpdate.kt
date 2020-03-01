package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key

/** Update response describing a removal from query result at [key] in [dataModel] */
data class RemovalUpdate<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val dataModel: DM,
    override val key: Key<DM>,
    override val version: ULong
) : IsUpdateResponse<DM, P>
