package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.query.requests.IsChangesRequest

fun IsChangesRequest<*, *>.checkMaxVersions(keepAllVersions: Boolean) {
    if (!keepAllVersions && this.maxVersions > 1u) {
        throw RequestException("Cannot use max versions larger than 1 on a table which has keepAllVersions set to false")
    }
}
