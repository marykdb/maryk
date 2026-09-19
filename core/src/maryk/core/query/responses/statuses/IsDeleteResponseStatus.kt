package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

/** Status for a delete object request */
interface IsDeleteResponseStatus<DM : IsRootDataModel> : IsResponseStatus
