package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.responses.IsResponse

/** Defines a Get by keys request. */
interface IsGetRequest<DM : IsRootDataModel, RP : IsResponse> : IsFetchRequest<DM, RP> {
    val keys: List<Key<DM>>
}
