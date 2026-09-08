package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse
import maryk.datastore.shared.updates.FlowSnapshotBoundary

/** A combination of a store [request] and a deferred [response]. Can be passed to a StoreExecutor */
class StoreAction<DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse>(
    val request: RQ,
    val response: CompletableDeferred<RP>,
    val onBeforeReadContext: (suspend () -> Unit)? = null,
    val onFlowSnapshotBoundary: (suspend (FlowSnapshotBoundary) -> Unit)? = null,
) {
    /** Opaque backend context captured when the request is submitted. */
    val executionContext: Any?
        get() = capturedExecutionContext

    private var capturedExecutionContext: Any? = null

    /** Adds backend request context without changing the legacy JVM constructor. */
    constructor(
        request: RQ,
        response: CompletableDeferred<RP>,
        onBeforeReadContext: (suspend () -> Unit)?,
        onFlowSnapshotBoundary: (suspend (FlowSnapshotBoundary) -> Unit)?,
        executionContext: Any?,
    ) : this(request, response, onBeforeReadContext, onFlowSnapshotBoundary) {
        capturedExecutionContext = executionContext
    }

    /** Whether this read establishes live-listener membership at a snapshot boundary. */
    val isFlowSnapshotRead get() = onFlowSnapshotBoundary != null
}
