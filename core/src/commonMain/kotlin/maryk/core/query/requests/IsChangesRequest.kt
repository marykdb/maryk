package maryk.core.query.requests

import maryk.core.properties.IsRootModel
import maryk.core.query.responses.IsResponse

/** Request for all versioned changes from a version and later */
interface IsChangesRequest<DM : IsRootModel, RP : IsResponse> :
    IsFetchRequest<DM, RP> {
    val fromVersion: ULong
    val maxVersions: UInt
}
