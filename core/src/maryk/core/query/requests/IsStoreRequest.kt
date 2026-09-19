package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.IsResponse

/** A request for a data store operation */
interface IsStoreRequest<out DM : IsRootDataModel, RP : IsResponse> : IsObjectRequest<DM, RP>
