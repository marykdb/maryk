package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel

/** Status for a change object request */
interface IsChangeResponseStatus<DM : IsRootModel> : IsAddOrChangeResponseStatus<DM>
