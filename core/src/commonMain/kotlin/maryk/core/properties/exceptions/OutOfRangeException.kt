package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.references.AnyPropertyReference

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
    override val validationExceptionType = ValidationExceptionType.OUT_OF_RANGE

    internal companion object: SimpleQueryDataModel<OutOfRangeException>(
        properties = object: ObjectPropertyDefinitions<OutOfRangeException>() {
            init {
                ValidationException.addReference(this, OutOfRangeException::reference)
                ValidationException.addValue(this, OutOfRangeException::value)
                add(3, "min", StringDefinition(required = false), OutOfRangeException::min)
                add(4, "max", StringDefinition(), OutOfRangeException::max)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<OutOfRangeException>) = OutOfRangeException(
            reference = map(1),
            value = map(2),
            min = map(3),
            max = map(4)
        )
    }
}
