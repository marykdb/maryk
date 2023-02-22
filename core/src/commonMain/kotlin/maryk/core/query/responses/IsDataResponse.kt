package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsValuesPropertyDefinitions

/** A response for a data operation on a DataModel which returns data values */
interface IsDataResponse<out DM : IsRootDataModel<P>, P: IsValuesPropertyDefinitions> : IsDataModelResponse<DM>
