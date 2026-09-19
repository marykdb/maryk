package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

/** Status for a change object request */
interface IsChangeResponseStatus<DM : IsRootDataModel> : IsAddOrChangeResponseStatus<DM>
