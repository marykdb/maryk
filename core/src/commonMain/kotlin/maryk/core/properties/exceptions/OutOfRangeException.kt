package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.ValidationExceptionType.OUT_OF_RANGE
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.SimpleObjectValues

/**
 * Exception for when a [value] of property referred by [reference] was
 * out of range of [min] and [max].
 *
 * This can be both of value or for the size of value containers like List or
 * Map
 */
data class OutOfRangeException internal constructor(
    val reference: AnyPropertyReference?,
    val value: String,
    val min: String?,
    val max: String?
) : ValidationException(
    reference = reference,
    reason = "is out of range: «$value» with range [$min,$max]"
) {
    override val validationExceptionType = OUT_OF_RANGE

    internal companion object : SimpleQueryDataModel<OutOfRangeException>(
        properties = object : ObjectPropertyDefinitions<OutOfRangeException>() {
            val reference by addReference(OutOfRangeException::reference)
            val value by string(2u, OutOfRangeException::value)
            val min by string(
                3u, OutOfRangeException::min,
                required = false
            )
            val max by string(
                4u, OutOfRangeException::max
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<OutOfRangeException>) = OutOfRangeException(
            reference = values(1u),
            value = values(2u),
            min = values(3u),
            max = values(4u)
        )
    }
}
