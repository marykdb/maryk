package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/**
 * Exception for when properties are set with invalid input.
 */
class PropertyInvalidValueException(
        ref: PropertyReference<*, *>,
        value: String
) : PropertyValidationException(
        reference = ref,
        id = "INVALID_VALUE",
        reason = "has invalid value: «$value»"
)
