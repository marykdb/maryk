package maryk.core.query.requests

import maryk.core.properties.IsRootModel
import maryk.core.query.responses.UpdatesResponse

/** Request for all updates */
interface IsUpdatesRequest<DM : IsRootModel, RP : UpdatesResponse<DM>> :
    IsChangesRequest<DM, RP>
