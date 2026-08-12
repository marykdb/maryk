package maryk.core.query.requests

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.IsResponse

internal const val MAX_HISTORY_VERSIONS = 1000u

internal fun validateMaxVersions(maxVersions: UInt, requestName: String) {
    if (maxVersions > MAX_HISTORY_VERSIONS) {
        throw RequestException("$requestName maxVersions $maxVersions exceeds maximum $MAX_HISTORY_VERSIONS")
    }
}

/** Request for all versioned changes from a version and later */
interface IsChangesRequest<DM : IsRootDataModel, RP : IsResponse> :
    IsFetchRequest<DM, RP> {
    val fromVersion: ULong
    val maxVersions: UInt
}
