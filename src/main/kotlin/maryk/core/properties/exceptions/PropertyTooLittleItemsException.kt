package maryk.core.properties.exceptions

import maryk.core.properties.references.IsPropertyReference

/** Exception for when a map or collection property has too little amount of items
 *
 * @param ref   of property
 * @param size  current size
 * @param minSize   minimum of range
 */
class PropertyTooLittleItemsException(
        ref: IsPropertyReference<*, *>,
        size: Int,
        minSize: Int
) : PropertyValidationException(
        reference = ref,
        id = "TOO_LITTLE_ITEMS",
        reason = "has not enough items: $size under minSize of $minSize items"
)
