package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.SimpleObjectValues

/**
 * Exception for when a map or collection property referred by [reference] of [size] has
 * too many items compared to [maxSize]
 */
data class TooManyItemsException internal constructor(
    val reference: AnyPropertyReference?,
    val size: UInt,
    val maxSize: UInt
) : ValidationException(
    reference = reference,
    reason = "has too many items: $size over max of $maxSize items"
) {
    override val validationExceptionType = ValidationExceptionType.TOO_MANY_ITEMS

    internal companion object: SimpleQueryDataModel<TooManyItemsException>(
        properties = object : ObjectPropertyDefinitions<TooManyItemsException>() {
            init {
                ValidationException.addReference(this, TooManyItemsException::reference)
                add(2, "size", NumberDefinition(type = UInt32), TooManyItemsException::size)
                add(3, "maxSize", NumberDefinition(type = UInt32), TooManyItemsException::maxSize)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<TooManyItemsException>) = TooManyItemsException(
            reference = values(1),
            size = values(2),
            maxSize = values(3)
        )
    }
}
