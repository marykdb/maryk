package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

/** Status for an add or change object request */
interface IsAddOrChangeResponseStatus<DM : IsRootDataModel<*>> : IsResponseStatus
