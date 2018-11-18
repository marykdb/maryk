package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.numeric.SInt32

/**
 * Exception for when a map or collection property referred by [reference] of [size] has
 * too much items compared to [maxSize]
 */
data class TooMuchItemsException internal constructor(
    val reference: AnyPropertyReference?,
    val size: Int,
    val maxSize: Int
) : ValidationException(
    reference = reference,
    reason = "has too much items: $size over max of $maxSize items"
) {
    override val validationExceptionType = ValidationExceptionType.TOO_MUCH_ITEMS

    internal companion object: SimpleQueryDataModel<TooMuchItemsException>(
        properties = object : ObjectPropertyDefinitions<TooMuchItemsException>() {
            init {
                ValidationException.addReference(this, TooMuchItemsException::reference)
                add(2, "size", NumberDefinition(type = SInt32), TooMuchItemsException::size)
                add(3, "maxSize", NumberDefinition(type = SInt32), TooMuchItemsException::maxSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<TooMuchItemsException>) = TooMuchItemsException(
            reference = values(1),
            size = values(2),
            maxSize = values(3)
        )
    }
}
