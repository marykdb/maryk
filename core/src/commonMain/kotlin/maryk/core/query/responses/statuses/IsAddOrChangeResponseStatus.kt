package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel

/** Status for an add or change object request */
interface IsAddOrChangeResponseStatus<DM : IsRootModel> : IsResponseStatus
