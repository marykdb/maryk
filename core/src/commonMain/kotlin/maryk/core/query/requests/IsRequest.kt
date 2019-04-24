package maryk.core.query.requests

import maryk.core.models.IsObjectDataModel
import maryk.core.query.responses.IsResponse

interface IsRequest<RP : IsResponse> {
    val requestType: RequestType
    val responseModel: IsObjectDataModel<in RP, *>
}
