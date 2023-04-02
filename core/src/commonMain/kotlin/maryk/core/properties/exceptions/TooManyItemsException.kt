package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.number
import maryk.core.properties.exceptions.ValidationExceptionType.TOO_MANY_ITEMS
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
    override val validationExceptionType = TOO_MANY_ITEMS

    @Suppress("unused")
    internal companion object : SimpleQueryModel<TooManyItemsException>() {
        val reference by addReference(TooManyItemsException::reference)
        val _size by number(2u, TooManyItemsException::size, UInt32, name = "size")
        val maxSize by number(3u, TooManyItemsException::maxSize, UInt32)

        override fun invoke(values: SimpleObjectValues<TooManyItemsException>) = TooManyItemsException(
            reference = values(1u),
            size = values(2u),
            maxSize = values(3u)
        )
    }
}
