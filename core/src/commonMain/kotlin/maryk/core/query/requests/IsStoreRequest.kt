package maryk.core.query.requests

import maryk.core.properties.IsRootModel
import maryk.core.query.responses.IsResponse

/** A request for a data store operation */
interface IsStoreRequest<out DM : IsRootModel, RP : IsResponse> : IsObjectRequest<DM, RP>
