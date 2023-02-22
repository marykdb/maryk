package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.query.responses.UpdatesResponse

/** Request for all updates */
interface IsUpdatesRequest<DM : IsRootDataModel<P>, P : IsValuesPropertyDefinitions, RP : UpdatesResponse<DM, P>> :
    IsChangesRequest<DM, P, RP>
