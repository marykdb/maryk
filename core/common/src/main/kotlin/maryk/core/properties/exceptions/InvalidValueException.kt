package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/** Exception for when properties referred by [reference] are set with invalid [value]. */
data class InvalidValueException internal constructor(
    val reference: IsPropertyReference<*, *>?,
    val value: String
) : ValidationException(
    reference = reference,
    reason = "has invalid value: «$value»"
) {
    override val validationExceptionType = ValidationExceptionType.INVALID_VALUE

    internal companion object: SimpleQueryDataModel<InvalidValueException>(
        properties = object: PropertyDefinitions<InvalidValueException>() {
            init {
                ValidationException.addReference(this, InvalidValueException::reference)
                ValidationException.addValue(this, InvalidValueException::value)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = InvalidValueException(
            reference = map[0] as IsPropertyReference<*, *>,
            value = map(1)
        )
    }
}
