package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/**
 * Validation Exception for properties
 */
abstract class PropertyValidationException (
        val reference: PropertyReference<*,*>? = null,
        val id: String,
        newMessage: String
) : Throwable(
        newMessage
) {
    constructor(
            reason: String?,
            reference: PropertyReference<*,*>?,
            id: String
    ) : this(
            reference = reference,
            id = id,
            newMessage = "Property «${reference?.completeName}» $reason"
    )
}
