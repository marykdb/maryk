package maryk.datastore.shared

import maryk.core.query.changes.IsChange
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.ObjectSoftDeleteChange
import maryk.core.query.requests.RequestType

/** The source operations needed to authorize committed changes, including empty change groups. */
fun changeRequestTypes(changes: List<IsChange>): Set<RequestType> =
    changes.mapTo(mutableSetOf()) { change ->
        when {
            change === ObjectCreate -> RequestType.Add
            change is ObjectSoftDeleteChange && change.isDeleted -> RequestType.Delete
            else -> RequestType.Change
        }
    }.ifEmpty { setOf(RequestType.Change) }
