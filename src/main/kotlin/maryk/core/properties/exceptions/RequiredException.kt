package maryk.core.properties.exceptions

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/** Exception if a required property referred by [reference] was not set or is being unset. */
data class RequiredException(
    val reference: IsPropertyReference<*, *>?
) : ValidationException(
    reference = reference,
    reason = "is required and not set"
) {
    override val validationExceptionType = ValidationExceptionType.REQUIRED

    internal companion object: QueryDataModel<RequiredException>(
        properties = object : PropertyDefinitions<RequiredException>() {
            init {
                ValidationException.addReference(this, RequiredException::reference)
            }
        }
    ) {
        override fun invoke(map: Map<Int, *>) = RequiredException(
            reference = map[0] as IsPropertyReference<*, *>?
        )
    }
}
