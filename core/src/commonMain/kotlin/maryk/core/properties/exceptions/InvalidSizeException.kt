package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
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
    override val validationExceptionType = ValidationExceptionType.INVALID_SIZE

    internal companion object : SimpleQueryDataModel<InvalidSizeException>(
        properties = object : ObjectPropertyDefinitions<InvalidSizeException>() {
            init {
                addReference(this, InvalidSizeException::reference)
                addValue(this, InvalidSizeException::value)
                add(3, "min", NumberDefinition(type = UInt32, required = false), InvalidSizeException::min)
                add(4, "max", NumberDefinition(type = UInt32, required = false), InvalidSizeException::max)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<InvalidSizeException>) = InvalidSizeException(
            reference = values(1),
            value = values(2),
            min = values(3),
            max = values(4)
        )
    }
}
