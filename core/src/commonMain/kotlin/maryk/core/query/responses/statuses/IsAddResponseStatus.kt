package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel

/** Status for an add object request */
interface IsAddResponseStatus<DM : IsRootDataModel<*>> : IsAddOrChangeResponseStatus<DM>
