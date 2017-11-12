package maryk.core.properties.exceptions

import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when a property is final and already has a value but was tried
 * to set to another value.
 *
 * @param reference of property
 */
class PropertyAlreadySetException(
        reference: IsPropertyReference<*, *>
) : PropertyValidationException(
        reference = reference,
        reason = "is already set before and cannot be set again",
        id = "ALREADY_SET"
)
