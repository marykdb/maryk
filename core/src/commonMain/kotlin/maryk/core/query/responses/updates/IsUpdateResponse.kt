package maryk.core.query.responses.updates

import maryk.core.properties.IsRootModel

/** A response describing an update to a data object */
interface IsUpdateResponse<DM: IsRootModel> {
    val type: UpdateResponseType
    val version: ULong
}
