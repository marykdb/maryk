package maryk.core.properties.exceptions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.exceptions.ValidationExceptionType.ALREADY_EXISTS
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.RequestContext
import maryk.core.values.SimpleObjectValues

/**
 * Exception for when a property referred by [reference] already exists at [key]
 * and was set as unique
 */
data class AlreadyExistsException(
    val reference: AnyPropertyReference?,
    val key: Key<*>
) : ValidationException(
    reference = reference,
    reason = "already exists and should be unique"
) {
    override val validationExceptionType = ALREADY_EXISTS

    @Suppress("unused")
    internal companion object : SimpleQueryModel<AlreadyExistsException>() {
        val reference by addReference(AlreadyExistsException::reference)
        val key by contextual(
            index = 2u,
            getter = AlreadyExistsException::key,
            definition = ContextualReferenceDefinition<RequestContext>(
                required = false,
                contextualResolver = {
                    it?.dataModel as IsRootDataModel<*>?
                        ?: throw ContextNotFoundException()
                }
            )
        )

        override fun invoke(values: SimpleObjectValues<AlreadyExistsException>) =
            AlreadyExistsException(
                reference = values(1u),
                key = values(2u)
            )
    }
}
