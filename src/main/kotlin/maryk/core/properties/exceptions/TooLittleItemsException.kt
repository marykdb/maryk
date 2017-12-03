package maryk.core.properties.exceptions

import maryk.core.objects.Def
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
        val reference: IsPropertyReference<*, *>,
        val size: Int,
        val minSize: Int
) : ValidationException(
        reference = reference,
        reason = "has not enough items: $size under minSize of $minSize items"
) {
    override val validationExceptionType = ValidationExceptionType.TOO_LITTLE_ITEMS

    internal object Properties : PropertyDefinitions<TooLittleItemsException>() {
        val size = NumberDefinition("size", 1, type = SInt32)
        val minSize = NumberDefinition("minSize", 2, type = SInt32)
    }

    companion object: QueryDataModel<TooLittleItemsException>(
            properties = object : PropertyDefinitions<TooLittleItemsException>() {
                init {
                    ValidationException.addReference(this, TooLittleItemsException::reference)
                    add(1, "size", NumberDefinition(type = SInt32), TooLittleItemsException::size)
                    add(2, "minSize", NumberDefinition(type = SInt32), TooLittleItemsException::minSize)
                }
            },
            definitions = listOf(
                    Def(ValidationException.Properties.reference, TooLittleItemsException::reference),
                    Def(Properties.size, TooLittleItemsException::size),
                    Def(Properties.minSize, TooLittleItemsException::minSize)
            )
    ) {
        override fun invoke(map: Map<Int, *>) = TooLittleItemsException(
                reference = map[0] as IsPropertyReference<*, *>,
                size = map[1] as Int,
                minSize = map[2] as Int
        )
    }
}
