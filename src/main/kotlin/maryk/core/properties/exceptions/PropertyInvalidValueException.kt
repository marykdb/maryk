package maryk.core.properties.exceptions

import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when properties are set with invalid input.
 */
class PropertyInvalidValueException(
        ref: IsPropertyReference<*, *>,
        value: String
) : PropertyValidationException(
        reference = ref,
        id = "INVALID_VALUE",
        reason = "has invalid value: «$value»"
)
