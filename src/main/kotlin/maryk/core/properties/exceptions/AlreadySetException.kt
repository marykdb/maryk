package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when a property is final and already has a value but was tried
 * to set to another value.
 *
 * @param reference of property
 */
data class AlreadySetException(
        val reference: IsPropertyReference<*, *>?
) : ValidationException(
        reference = reference,
        reason = "is already set before and cannot be set again"
) {
    override val validationExceptionType = ValidationExceptionType.ALREADY_SET

    internal object Properties : PropertyDefinitions<AlreadySetException>() {
        init {
            add(
                    0, "reference",
                    ValidationException.Properties.reference,
                    AlreadySetException::reference
            )
        }
    }

    companion object: QueryDataModel<AlreadySetException>(
            properties = AlreadySetException.Properties
    ) {
        override fun invoke(map: Map<Int, *>) = AlreadySetException(
                reference = map[0] as IsPropertyReference<*, *>
        )
    }
}
