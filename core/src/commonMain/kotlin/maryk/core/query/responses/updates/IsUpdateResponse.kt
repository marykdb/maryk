package maryk.core.query.responses.updates

import maryk.core.models.IsRootDataModel

/** A response describing an update to a data object */
interface IsUpdateResponse<DM: IsRootDataModel> {
    val type: UpdateResponseType
    val version: ULong
}
