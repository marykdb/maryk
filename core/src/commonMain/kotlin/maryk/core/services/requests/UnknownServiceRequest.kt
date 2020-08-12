package maryk.core.services.requests

/** Unknown request type to service */
class UnknownServiceRequest(
    index: UInt,
    override val name: String
): ServiceRequestType<IsServiceRequest>(index, null)
