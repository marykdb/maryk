package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.numeric.SInt32

/**
 * Exception for property with [value] referred by [reference]
 * which got a wrong new size outside [min] and [max].
 */
data class InvalidSizeException(
    val reference: IsPropertyReference<*, *>?,
    val value: String,
    val min: Int?,
    val max: Int?
) : ValidationException(
    reference = reference,
    reason = "has incorrect size: «$value» with size limits [$min,$max]"
) {
    override val validationExceptionType = ValidationExceptionType.INVALID_SIZE

    internal companion object: QueryDataModel<InvalidSizeException>(
        properties = object: PropertyDefinitions<InvalidSizeException>() {
            init {
                ValidationException.addReference(this, InvalidSizeException::reference)
                ValidationException.addValue(this, InvalidSizeException::value)
                add(2, "min", NumberDefinition(type = SInt32, required = false), InvalidSizeException::min)
                add(3, "max", NumberDefinition(type = SInt32, required = false), InvalidSizeException::max)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = InvalidSizeException(
            reference = map[0] as IsPropertyReference<*, *>,
            value = map[1] as String,
            min = map[2] as Int?,
            max = map[3] as Int?
        )
    }
}
