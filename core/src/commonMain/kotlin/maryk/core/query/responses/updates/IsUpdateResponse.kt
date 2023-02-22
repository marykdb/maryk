package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions

/** A response describing an update to a data object */
interface IsUpdateResponse<DM: IsRootDataModel<P>, P: IsValuesPropertyDefinitions> {
    val type: UpdateResponseType
    val version: ULong
}
