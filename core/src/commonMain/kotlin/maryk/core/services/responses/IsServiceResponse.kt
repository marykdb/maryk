package maryk.core.services.responses

import maryk.core.services.IsServicePacket

/**
 * Interface for service response implementations
 * Contains an [id] to map requests with responses
 */
interface IsServiceResponse : IsServicePacket {
    val id: ULong
}
