package maryk.core.query.responses

import maryk.core.models.IsRootDataModel

/** A response for a data operation on a DataModel which returns data values */
interface IsDataResponse<out DM : IsRootDataModel> : IsDataModelResponse<DM>
