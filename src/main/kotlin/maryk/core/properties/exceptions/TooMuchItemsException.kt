package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.SInt32

/** Exception for when a map or collection property has too much items
 *
 * @param ref   of property
 * @param size   current size
 * @param maxSize  maximum of size
 */
data class TooMuchItemsException(
        val reference: IsPropertyReference<*, *>,
        val size: Int,
        val maxSize: Int
) : ValidationException(
        reference = reference,
        reason = "has too much items: $size over max of $maxSize items"
) {
    override val validationExceptionType = ValidationExceptionType.TOO_MUCH_ITEMS

    internal object Properties {
        val size = NumberDefinition("size", 1, type = SInt32)
        val maxSize = NumberDefinition("maxSize", 2, type = SInt32)
    }
    
    companion object: QueryDataModel<TooMuchItemsException>(
            construct = {
                TooMuchItemsException(
                        reference = it[0] as IsPropertyReference<*, *>,
                        size = it[1] as Int,
                        maxSize = it[2] as Int
                )
            },
            definitions = listOf(
                    Def(ValidationException.Properties.reference, TooMuchItemsException::reference),
                    Def(Properties.size, TooMuchItemsException::size),
                    Def(Properties.maxSize, TooMuchItemsException::maxSize)
            )
    )
}
