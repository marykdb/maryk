package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.IsResponse

/** Request for all versioned changes from a version and later */
interface IsChangesRequest<DM : IsRootDataModel, RP : IsResponse> :
    IsFetchRequest<DM, RP> {
    val fromVersion: ULong
    val maxVersions: UInt
}
