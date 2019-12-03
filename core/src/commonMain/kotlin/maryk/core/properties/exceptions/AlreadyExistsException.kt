package maryk.core.properties.exceptions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
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

    internal companion object : SimpleQueryDataModel<AlreadyExistsException>(
        properties = object : ObjectPropertyDefinitions<AlreadyExistsException>() {
            init {
                addReference(this, AlreadyExistsException::reference)
                add(
                    index = 2u, name = "key",
                    definition = ContextualReferenceDefinition<RequestContext>(
                        required = false,
                        contextualResolver = {
                            it?.dataModel as IsRootValuesDataModel<*>?
                                ?: throw ContextNotFoundException()
                        }
                    ),
                    getter = AlreadyExistsException::key
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AlreadyExistsException>) = AlreadyExistsException(
            reference = values(1u),
            key = values(2u)
        )
    }
}
