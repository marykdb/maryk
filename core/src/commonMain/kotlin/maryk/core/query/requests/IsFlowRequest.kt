package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.IsDataResponse

/** Defines a request which can open a live update flow. */
interface IsFlowRequest<DM : IsRootDataModel, RP : IsDataResponse<DM>> : IsFetchRequest<DM, RP>
