package maryk.core.query.requests

import maryk.core.models.IsTypedObjectModel
import maryk.core.query.RequestContext
import maryk.core.query.responses.IsResponse

/**
 * Defines a request which can be transported because of a [responseModel]
 * which describes how it can be serialized
 */
interface IsTransportableRequest<RP : IsResponse> : IsRequest<RP> {
    val requestType: RequestType
    val responseModel: IsTypedObjectModel<in RP, *, RequestContext, RequestContext>
}
