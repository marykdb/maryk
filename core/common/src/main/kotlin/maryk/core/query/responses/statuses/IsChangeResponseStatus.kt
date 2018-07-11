package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

@Suppress("unused")
/** Status for a change object request */
interface IsChangeResponseStatus<DM: IsRootDataModel<*>> : IsResponseStatus
