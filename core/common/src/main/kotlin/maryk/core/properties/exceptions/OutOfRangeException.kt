package maryk.core.properties.exceptions

import maryk.core.models.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when a [value] of property referred by [reference] was
 * out of range of [min] and [max].
 *
 * This can be both of value or for the size of value containers like List or
 * Map
 */
data class OutOfRangeException internal constructor(
    val reference: IsPropertyReference<*, *>?,
    val value: String,
    val min: String?,
    val max: String?
) : ValidationException(
    reference = reference,
    reason = "is out of range: «$value» with range [$min,$max]"
) {
    override val validationExceptionType = ValidationExceptionType.OUT_OF_RANGE

    internal companion object: QueryDataModel<OutOfRangeException>(
        properties = object: PropertyDefinitions<OutOfRangeException>() {
            init {
                ValidationException.addReference(this, OutOfRangeException::reference)
                ValidationException.addValue(this, OutOfRangeException::value)
                add(2, "min", StringDefinition(required = false), OutOfRangeException::min)
                add(3, "max", StringDefinition(), OutOfRangeException::max)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = OutOfRangeException(
            reference = map(0),
            value = map(1),
            min = map(2),
            max = map(3)
        )
    }
}
