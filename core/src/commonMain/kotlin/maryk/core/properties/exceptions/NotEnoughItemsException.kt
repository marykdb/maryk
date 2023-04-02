package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.number
import maryk.core.properties.exceptions.ValidationExceptionType.NOT_ENOUGH_ITEMS
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
    override val validationExceptionType = NOT_ENOUGH_ITEMS

    @Suppress("unused")
    internal companion object : SimpleQueryModel<NotEnoughItemsException>() {
        val reference by addReference(NotEnoughItemsException::reference)
        // Override name since size is reserved
        val _size by number(2u, NotEnoughItemsException::size, UInt32, name = "size")
        val minSize by number(3u, NotEnoughItemsException::minSize, UInt32)

        override fun invoke(values: SimpleObjectValues<NotEnoughItemsException>) = NotEnoughItemsException(
            reference = values(1u),
            size = values(2u),
            minSize = values(3u)
        )
    }
}
