package maryk.core.properties.exceptions

import maryk.core.properties.references.PropertyReference

/** Exception for when a map or collection property has too much items
 *
 * @param ref   of property
 * @param size   current size
 * @param maxSize  maximum of size
 */
class PropertyTooMuchItemsException(
        ref: PropertyReference<*, *>,
        size: Int,
        maxSize: Int
) : PropertyValidationException(
        reference = ref,
        id = "TOO_MUCH_ITEMS",
        reason = "has too much items: $size over max of $maxSize items"
)
