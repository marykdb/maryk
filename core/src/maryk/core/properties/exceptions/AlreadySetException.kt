package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.exceptions.ValidationExceptionType.ALREADY_SET
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.SimpleObjectValues

/**
 * Exception for when a property referred by [reference] is final and already has a value but was tried
 * to set to another value.
 */
data class AlreadySetException(
    val reference: AnyPropertyReference?
) : ValidationException(
    reference = reference,
    reason = "is already set before and cannot be set again"
) {
    override val validationExceptionType = ALREADY_SET

    internal companion object : SimpleQueryModel<AlreadySetException>() {
        val reference by addReference(AlreadySetException::reference)

        override fun invoke(values: SimpleObjectValues<AlreadySetException>) =
            AlreadySetException(
                reference = values(1u)
            )
    }
}
