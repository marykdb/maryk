package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/**
 * Exception if a required property was not set or is being unset.
 * @param ref to the required property
 */
class PropertyRequiredException(
        ref: PropertyReference<*,*>
) : PropertyValidationException(
        reference = ref,
        id = "REQUIRED",
        reason = "is required and not set"
)
