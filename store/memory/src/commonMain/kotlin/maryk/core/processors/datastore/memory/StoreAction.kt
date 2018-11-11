package maryk.core.processors.datastore.memory

import kotlinx.coroutines.CompletableDeferred
import maryk.core.models.IsRootDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsStoreRequest
import maryk.core.query.responses.IsResponse

internal class StoreAction<DM: IsRootDataModel<P>, P: PropertyDefinitions, RQ: IsStoreRequest<DM, RP>, RP: IsResponse>(
    val request: RQ,
    val response: CompletableDeferred<RP>
)
