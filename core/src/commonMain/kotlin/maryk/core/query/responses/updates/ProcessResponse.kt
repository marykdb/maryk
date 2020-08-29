package maryk.core.query.responses.updates

import maryk.core.models.IsRootValuesDataModel
import maryk.core.query.responses.IsDataModelResponse
import maryk.core.query.responses.IsResponse

/** [result] to processing an UpdateResponse of [version]*/
class ProcessResponse<DM: IsRootValuesDataModel<*>>(
    val version: ULong,
    val result: IsDataModelResponse<DM>
) : IsResponse
