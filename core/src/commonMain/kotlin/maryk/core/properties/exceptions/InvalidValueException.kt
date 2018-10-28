package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.AnyPropertyReference

/** Exception for when properties referred by [reference] are set with invalid [value]. */
data class InvalidValueException internal constructor(
    val reference: AnyPropertyReference?,
    val value: String
) : ValidationException(
    reference = reference,
    reason = "has invalid value: «$value»"
) {
    override val validationExceptionType = ValidationExceptionType.INVALID_VALUE

    internal companion object: SimpleQueryDataModel<InvalidValueException>(
        properties = object: ObjectPropertyDefinitions<InvalidValueException>() {
            init {
                ValidationException.addReference(this, InvalidValueException::reference)
                ValidationException.addValue(this, InvalidValueException::value)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<InvalidValueException>) = InvalidValueException(
            reference = map(1),
            value = map(2)
        )
    }
}
