package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.numeric.SInt32

/**
 * Exception for property with [value] referred by [reference]
 * which got a wrong new size outside [min] and [max].
 */
data class InvalidSizeException internal constructor(
    val reference: AnyPropertyReference?,
    val value: String,
    val min: Int?,
    val max: Int?
) : ValidationException(
    reference = reference,
    reason = "has incorrect size: «$value» with size limits [$min,$max]"
) {
    override val validationExceptionType = ValidationExceptionType.INVALID_SIZE

    internal companion object: SimpleQueryDataModel<InvalidSizeException>(
        properties = object: ObjectPropertyDefinitions<InvalidSizeException>() {
            init {
                ValidationException.addReference(this, InvalidSizeException::reference)
                ValidationException.addValue(this, InvalidSizeException::value)
                add(3, "min", NumberDefinition(type = SInt32, required = false), InvalidSizeException::min)
                add(4, "max", NumberDefinition(type = SInt32, required = false), InvalidSizeException::max)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<InvalidSizeException>) = InvalidSizeException(
            reference = map(1),
            value = map(2),
            min = map(3),
            max = map(4)
        )
    }
}
