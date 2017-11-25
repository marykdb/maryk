package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception for when a property is final and already has a value but was tried
 * to set to another value.
 *
 * @param reference of property
 */
data class AlreadySetException(
        val reference: IsPropertyReference<*, *>
) : ValidationException(
        reference = reference,
        reason = "is already set before and cannot be set again"
) {
    override val validationExceptionType = ValidationExceptionType.ALREADY_SET

    companion object: QueryDataModel<AlreadySetException>(
            construct = {
                AlreadySetException(
                        reference = it[0] as IsPropertyReference<*, *>
                )
            },
            definitions = listOf(
                    Def(Properties.reference, AlreadySetException::reference)
            )
    )
}
