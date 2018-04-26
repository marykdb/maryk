package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.SInt32

/**
 * Exception for when a map or collection property referred by [reference] of [size] has
 * not enough items compared to [minSize]
 */
data class NotEnoughItemsException internal constructor(
    val reference: IsPropertyReference<*, *>?,
    val size: Int,
    val minSize: Int
) : ValidationException(
    reference = reference,
    reason = "has not enough items: $size under minSize of $minSize items"
) {
    override val validationExceptionType = ValidationExceptionType.NOT_ENOUGH_ITEMS

    internal companion object: QueryDataModel<NotEnoughItemsException>(
        properties = object : PropertyDefinitions<NotEnoughItemsException>() {
            init {
                ValidationException.addReference(this, NotEnoughItemsException::reference)
                add(1, "size", NumberDefinition(type = SInt32), NotEnoughItemsException::size)
                add(2, "minSize", NumberDefinition(type = SInt32), NotEnoughItemsException::minSize)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = NotEnoughItemsException(
            reference = map[0] as IsPropertyReference<*, *>,
            size = map[1] as Int,
            minSize = map[2] as Int
        )
    }
}