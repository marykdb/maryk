package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/**
 * Exception for when a value was out of range.
 *
 * This can be both of value or for the size of value containers like List or
 * Map
 *
 * @param ref   of property
 * @param value which was invalid
 * @param min   minimum of range
 * @param max   maximum of range
 */
class PropertyOutOfRangeException(
        ref: PropertyReference<*, *>,
        value: Any,
        min: Any?,
        max: Any?
) : PropertyValidationException(
        reference = ref,
        id = "OUT_OF_RANGE",
        reason = "is out of range: «$value» with range [$min,$max]"
)
