package maryk.datastore.shared

import maryk.core.query.requests.AddRequest
import maryk.core.query.requests.ChangeRequest
import maryk.core.query.requests.DeleteRequest
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.requests.GetRequest
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.ScanUpdateHistoryRequest
import maryk.core.query.requests.ScanUpdatesRequest

enum class RequestExecutionKind {
    Read,
    Mutation,
}

val Any.requestExecutionKind: RequestExecutionKind
    get() = when (this) {
        is GetRequest<*>,
        is GetChangesRequest<*>,
        is GetUpdatesRequest<*>,
        is ScanRequest<*>,
        is ScanChangesRequest<*>,
        is ScanUpdateHistoryRequest<*>,
        is ScanUpdatesRequest<*> -> RequestExecutionKind.Read
        is AddRequest<*>,
        is ChangeRequest<*>,
        is DeleteRequest<*> -> RequestExecutionKind.Mutation
        else -> RequestExecutionKind.Mutation
    }
