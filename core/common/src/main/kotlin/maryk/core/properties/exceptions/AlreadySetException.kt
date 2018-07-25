package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when a property referred by [reference] is final and already has a value but was tried
 * to set to another value.
 */
data class AlreadySetException internal constructor(
    val reference: IsPropertyReference<*, *>?
) : ValidationException(
    reference = reference,
    reason = "is already set before and cannot be set again"
) {
    override val validationExceptionType = ValidationExceptionType.ALREADY_SET

    internal companion object: SimpleQueryDataModel<AlreadySetException>(
        properties = object : ObjectPropertyDefinitions<AlreadySetException>() {
            init {
                ValidationException.addReference(this, AlreadySetException::reference)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<AlreadySetException>) = AlreadySetException(
            reference = map(1)
        )
    }
}
