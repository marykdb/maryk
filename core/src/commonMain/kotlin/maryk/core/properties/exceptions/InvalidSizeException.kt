package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
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
            init {
                addReference(this, InvalidSizeException::reference)
                addValue(this, InvalidSizeException::value)
                add(3u, "min", NumberDefinition(type = UInt32, required = false), InvalidSizeException::min)
                add(4u, "max", NumberDefinition(type = UInt32, required = false), InvalidSizeException::max)
            }
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
