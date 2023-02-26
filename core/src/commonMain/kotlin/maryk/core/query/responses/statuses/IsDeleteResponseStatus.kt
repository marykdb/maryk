package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel

/** Status for a delete object request */
interface IsDeleteResponseStatus<DM : IsRootModel> : IsResponseStatus
