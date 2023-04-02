package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import maryk.core.models.IsRootDataModel
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse

/** A combination of a store [request] and a deferred [response]. Can be passed to a StoreExecutor */
class StoreAction<DM : IsRootDataModel, RQ : IsStoreRequest<DM, RP>, RP : IsResponse>(
    val request: RQ,
    val response: CompletableDeferred<RP>
)
