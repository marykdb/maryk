package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/**
 * Exception for properties which got a wrong new size.
 */
class PropertyInvalidSizeException(
        ref: PropertyReference<*, *>,
        value: String,
        min: Int?,
        max: Int?
) : PropertyValidationException(
        reference = ref,
        id = "INVALID_SIZE",
        reason = "has incorrect length: «$value» with size limits [$min,$max]"
)
