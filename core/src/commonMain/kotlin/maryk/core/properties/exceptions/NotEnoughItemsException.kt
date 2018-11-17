package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.numeric.SInt32

/**
 * Exception for when a map or collection property referred by [reference] of [size] has
 * not enough items compared to [minSize]
 */
data class NotEnoughItemsException internal constructor(
    val reference: AnyPropertyReference?,
    val size: Int,
    val minSize: Int
) : ValidationException(
    reference = reference,
    reason = "has not enough items: $size under minSize of $minSize items"
) {
    override val validationExceptionType = ValidationExceptionType.NOT_ENOUGH_ITEMS

    internal companion object: SimpleQueryDataModel<NotEnoughItemsException>(
        properties = object : ObjectPropertyDefinitions<NotEnoughItemsException>() {
            init {
                ValidationException.addReference(this, NotEnoughItemsException::reference)
                add(2, "size", NumberDefinition(type = SInt32), NotEnoughItemsException::size)
                add(3, "minSize", NumberDefinition(type = SInt32), NotEnoughItemsException::minSize)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<NotEnoughItemsException>) = NotEnoughItemsException(
            reference = map(1),
            size = map(2),
            minSize = map(3)
        )
    }
}
