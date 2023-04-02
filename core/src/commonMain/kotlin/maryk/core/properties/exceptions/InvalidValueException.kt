package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.string
import maryk.core.properties.exceptions.ValidationExceptionType.INVALID_VALUE
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.SimpleObjectValues

/** Exception for when properties referred by [reference] are set with invalid [value]. */
data class InvalidValueException(
    val reference: AnyPropertyReference?,
    val value: String
) : ValidationException(
    reference = reference,
    reason = "has invalid value: «$value»"
) {
    override val validationExceptionType = INVALID_VALUE

    internal companion object : SimpleQueryModel<InvalidValueException>() {
        val reference by addReference(InvalidValueException::reference)
        val value by string(2u, InvalidValueException::value)

        override fun invoke(values: SimpleObjectValues<InvalidValueException>) = InvalidValueException(
            reference = values(1u),
            value = values(2u)
        )
    }
}
