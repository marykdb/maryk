package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.SInt32

/** Exception for when a map or collection property has too little amount of items
 *
 * @param reference   of property
 * @param size  current size
 * @param minSize   minimum of range
 */
data class TooLittleItemsException(
        val reference: IsPropertyReference<*, *>?,
        val size: Int,
        val minSize: Int
) : ValidationException(
        reference = reference,
        reason = "has not enough items: $size under minSize of $minSize items"
) {
    override val validationExceptionType = ValidationExceptionType.TOO_LITTLE_ITEMS

    companion object: QueryDataModel<TooLittleItemsException>(
            properties = object : PropertyDefinitions<TooLittleItemsException>() {
                init {
                    ValidationException.addReference(this, TooLittleItemsException::reference)
                    add(1, "size", NumberDefinition(type = SInt32), TooLittleItemsException::size)
                    add(2, "minSize", NumberDefinition(type = SInt32), TooLittleItemsException::minSize)
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = TooLittleItemsException(
                reference = map[0] as IsPropertyReference<*, *>,
                size = map[1] as Int,
                minSize = map[2] as Int
        )
    }
}
