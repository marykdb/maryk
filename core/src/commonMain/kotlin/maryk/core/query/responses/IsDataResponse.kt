package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyDefinitions

/** A response for a data operation on a DataModel which returns data values */
interface IsDataResponse<out DM : IsRootDataModel<P>, P: IsPropertyDefinitions> : IsDataModelResponse<DM>
