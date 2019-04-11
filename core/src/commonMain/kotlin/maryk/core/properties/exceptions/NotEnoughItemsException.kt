package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.SimpleObjectValues

/**
 * Exception for when a map or collection property referred by [reference] of [size] has
 * not enough items compared to [minSize]
 */
data class NotEnoughItemsException internal constructor(
    val reference: AnyPropertyReference?,
    val size: UInt,
    val minSize: UInt
) : ValidationException(
    reference = reference,
    reason = "has not enough items: $size under minSize of $minSize items"
) {
    override val validationExceptionType = ValidationExceptionType.NOT_ENOUGH_ITEMS

    internal companion object : SimpleQueryDataModel<NotEnoughItemsException>(
        properties = object : ObjectPropertyDefinitions<NotEnoughItemsException>() {
            init {
                addReference(this, NotEnoughItemsException::reference)
                add(2, "size", NumberDefinition(type = UInt32), NotEnoughItemsException::size)
                add(3, "minSize", NumberDefinition(type = UInt32), NotEnoughItemsException::minSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<NotEnoughItemsException>) = NotEnoughItemsException(
            reference = values(1),
            size = values(2),
            minSize = values(3)
        )
    }
}
