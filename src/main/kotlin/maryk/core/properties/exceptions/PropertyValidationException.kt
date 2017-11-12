package maryk.core.properties.exceptions

import maryk.core.properties.references.IsPropertyReference

/**
 * Validation Exception for properties
 */
abstract class PropertyValidationException (
        val reference: IsPropertyReference<*, *>? = null,
        val id: String,
        newMessage: String
) : Throwable(
        newMessage
) {
    constructor(
            reason: String?,
            reference: IsPropertyReference<*,*>?,
            id: String
    ) : this(
            reference = reference,
            id = id,
            newMessage = "Property «${reference?.completeName}» $reason"
    )
}
