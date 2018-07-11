package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.IsPropertyReference

/** Exception if a required property referred by [reference] was not set or is being unset. */
data class RequiredException internal constructor(
    val reference: IsPropertyReference<*, *>?
) : ValidationException(
    reference = reference,
    reason = "is required and not set"
) {
    override val validationExceptionType = ValidationExceptionType.REQUIRED

    internal companion object: SimpleQueryDataModel<RequiredException>(
        properties = object : ObjectPropertyDefinitions<RequiredException>() {
            init {
                ValidationException.addReference(this, RequiredException::reference)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<RequiredException>) = RequiredException(
            reference = map(0)
        )
    }
}
