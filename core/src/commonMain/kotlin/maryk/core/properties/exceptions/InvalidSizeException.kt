package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.ValidationExceptionType.INVALID_SIZE
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.SimpleObjectValues

/**
 * Exception for property with [value] referred by [reference]
 * which got a wrong new size outside [min] and [max].
 */
data class InvalidSizeException internal constructor(
    val reference: AnyPropertyReference?,
    val value: String,
    val min: UInt?,
    val max: UInt?
) : ValidationException(
    reference = reference,
    reason = "has incorrect size: «$value» with size limits [$min,$max]"
) {
    override val validationExceptionType = INVALID_SIZE

    internal companion object : SimpleQueryDataModel<InvalidSizeException>(
        properties = object : ObjectPropertyDefinitions<InvalidSizeException>() {
            val reference by addReference(InvalidSizeException::reference)
            val value by string(2u, InvalidSizeException::value)
            val min by number(3u, InvalidSizeException::min, type = UInt32, required = false)
            val max by number(4u, InvalidSizeException::max, type = UInt32, required = false)
        }
    ) {
        override fun invoke(values: SimpleObjectValues<InvalidSizeException>) = InvalidSizeException(
            reference = values(1u),
            value = values(2u),
            min = values(3u),
            max = values(4u)
        )
    }
}
