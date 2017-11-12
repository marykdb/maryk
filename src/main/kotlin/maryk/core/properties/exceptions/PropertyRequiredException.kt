package maryk.core.properties.exceptions

import maryk.core.properties.references.IsPropertyReference

/**
 * Exception if a required property was not set or is being unset.
 * @param ref to the required property
 */
class PropertyRequiredException(
        ref: IsPropertyReference<*, *>
) : PropertyValidationException(
        reference = ref,
        id = "REQUIRED",
        reason = "is required and not set"
)
