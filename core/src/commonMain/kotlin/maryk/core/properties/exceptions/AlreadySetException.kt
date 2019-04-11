package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
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
    override val validationExceptionType = ValidationExceptionType.ALREADY_SET

    internal companion object : SimpleQueryDataModel<AlreadySetException>(
        properties = object : ObjectPropertyDefinitions<AlreadySetException>() {
            init {
                addReference(this, AlreadySetException::reference)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AlreadySetException>) = AlreadySetException(
            reference = values(1)
        )
    }
}
