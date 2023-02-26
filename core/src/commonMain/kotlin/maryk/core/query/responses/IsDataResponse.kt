package maryk.core.query.responses

import maryk.core.properties.IsRootModel

/** A response for a data operation on a DataModel which returns data values */
interface IsDataResponse<out DM : IsRootModel> : IsDataModelResponse<DM>
