package maryk.core.properties.exceptions

import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for properties which got a wrong new size.
 */
class PropertyInvalidSizeException(
        ref: IsPropertyReference<*, *>,
        value: String,
        min: Int?,
        max: Int?
) : PropertyValidationException(
        reference = ref,
        id = "INVALID_SIZE",
        reason = "has incorrect size: «$value» with size limits [$min,$max]"
)
