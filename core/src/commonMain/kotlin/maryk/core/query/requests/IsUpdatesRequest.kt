package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.query.responses.UpdatesResponse

/** Request for all updates */
interface IsUpdatesRequest<DM : IsRootDataModel, RP : UpdatesResponse<DM>> :
    IsChangesRequest<DM, RP>
