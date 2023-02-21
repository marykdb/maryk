package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyDefinitions

/** A response describing an update to a data object */
interface IsUpdateResponse<DM: IsRootDataModel<P>, P: IsPropertyDefinitions> {
    val type: UpdateResponseType
    val version: ULong
}
