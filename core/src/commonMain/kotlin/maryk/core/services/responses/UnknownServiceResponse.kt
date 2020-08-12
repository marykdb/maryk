package maryk.core.services.responses

/** Unknown response type from service */
class UnknownServiceResponse(
    index: UInt,
    override val name: String
): ServiceResponseType<IsServiceResponse>(index, null)
