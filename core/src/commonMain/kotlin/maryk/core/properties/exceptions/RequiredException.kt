package maryk.core.properties.exceptions

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.exceptions.ValidationExceptionType.REQUIRED
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.values.SimpleObjectValues

/** Exception if a required property referred by [reference] was not set or is being unset. */
data class RequiredException internal constructor(
    val reference: AnyPropertyReference?
) : ValidationException(
    reference = reference,
    reason = "is required and not set"
) {
    override val validationExceptionType = REQUIRED

    internal companion object : SimpleQueryModel<RequiredException>() {
        val reference by addReference(RequiredException::reference)

        override fun invoke(values: SimpleObjectValues<RequiredException>) = RequiredException(
            reference = values(1u)
        )
    }
}
