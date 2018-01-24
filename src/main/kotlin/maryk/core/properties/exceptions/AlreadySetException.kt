package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when a property referred by [reference] is final and already has a value but was tried
 * to set to another value.
 */
data class AlreadySetException(
    val reference: IsPropertyReference<*, *>?
) : ValidationException(
    reference = reference,
    reason = "is already set before and cannot be set again"
) {
    override val validationExceptionType = ValidationExceptionType.ALREADY_SET

    internal companion object: QueryDataModel<AlreadySetException>(
        properties = object : PropertyDefinitions<AlreadySetException>() {
            init {
                ValidationException.addReference(this, AlreadySetException::reference)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = AlreadySetException(
            reference = map[0] as IsPropertyReference<*, *>
        )
    }
}
