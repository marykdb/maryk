package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.SimpleObjectValues

/** Exception if a required property referred by [reference] was not set or is being unset. */
data class RequiredException internal constructor(
    val reference: AnyPropertyReference?
) : ValidationException(
    reference = reference,
    reason = "is required and not set"
) {
    override val validationExceptionType = ValidationExceptionType.REQUIRED

    internal companion object : SimpleQueryDataModel<RequiredException>(
        properties = object : ObjectPropertyDefinitions<RequiredException>() {
            init {
                addReference(this, RequiredException::reference)
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<RequiredException>) = RequiredException(
            reference = values(1u)
        )
    }
}
