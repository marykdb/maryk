package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

@Suppress("unused")
/** Status for an add object request */
interface IsAddResponseStatus<DM : IsRootDataModel<*>> : IsResponseStatus
