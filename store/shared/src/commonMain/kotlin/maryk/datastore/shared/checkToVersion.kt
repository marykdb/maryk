package maryk.datastore.shared

import maryk.core.exceptions.RequestException
import maryk.core.query.requests.IsFetchRequest

/**
 * Checks if toVersion is allowed on request. It is only allowed if [keepAllVersions] is true.
 * Otherwise a request exception is thrown
 */
fun IsFetchRequest<*, *, *>.checkToVersion(keepAllVersions: Boolean) {
    if (!keepAllVersions && this.toVersion != null) {
        throw RequestException("Cannot use toVersion on a table which has keepAllVersions set to false")
    }
}
