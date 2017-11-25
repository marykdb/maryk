package maryk.core.properties.exceptions

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.references.IsPropertyReference

/**
 * Exception if a required property was not set or is being unset.
 * @param ref to the required property
 */
data class RequiredException(
        val reference: IsPropertyReference<*, *>
) : ValidationException(
        reference = reference,
        reason = "is required and not set"
) {
    override val validationExceptionType = ValidationExceptionType.REQUIRED

    companion object: QueryDataModel<RequiredException>(
            construct = {
                RequiredException(
                        reference = it[0] as IsPropertyReference<*, *>
                )
            },
            definitions = listOf(
                    Def(ValidationException.Properties.reference, RequiredException::reference)
            )
    )
}
