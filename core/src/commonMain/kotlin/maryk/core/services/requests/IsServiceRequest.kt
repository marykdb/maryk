package maryk.core.services.requests

import maryk.core.services.IsServicePacket

/**
 * Interface for service request implementations
 * Contains an [id] to be able to map responses
 */
interface IsServiceRequest : IsServicePacket {
    val id: ULong
}
