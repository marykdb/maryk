package maryk.core.query.responses.statuses

import maryk.core.properties.IsRootModel

/** Status for an add object request */
interface IsAddResponseStatus<DM : IsRootModel> : IsAddOrChangeResponseStatus<DM>
