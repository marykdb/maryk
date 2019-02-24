package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

@Suppress("unused")
/** Status for a delete object request */
interface IsDeleteResponseStatus<DM : IsRootDataModel<*>> : IsResponseStatus
