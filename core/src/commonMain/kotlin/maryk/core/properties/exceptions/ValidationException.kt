package maryk.core.properties.exceptions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsSerializablePropertyDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.contextual
import maryk.core.properties.exceptions.ValidationExceptionType.ALREADY_EXISTS
import maryk.core.properties.exceptions.ValidationExceptionType.ALREADY_SET
import maryk.core.properties.exceptions.ValidationExceptionType.INVALID_SIZE
import maryk.core.properties.exceptions.ValidationExceptionType.INVALID_VALUE
import maryk.core.properties.exceptions.ValidationExceptionType.NOT_ENOUGH_ITEMS
import maryk.core.properties.exceptions.ValidationExceptionType.OUT_OF_RANGE
import maryk.core.properties.exceptions.ValidationExceptionType.REQUIRED
import maryk.core.properties.exceptions.ValidationExceptionType.TOO_MANY_ITEMS
import maryk.core.properties.exceptions.ValidationExceptionType.UMBRELLA
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.RequestContext

/** Validation Exception with newMessage for properties */
abstract class ValidationException internal constructor(
    newMessage: String
) : Throwable(
    newMessage
) {
    internal constructor(
        reason: String?,
        reference: AnyPropertyReference?
    ) : this(
        newMessage = "Property «${reference?.completeName}» $reason"
    )

    internal abstract val validationExceptionType: ValidationExceptionType
}

internal fun <DO : ValidationException> ObjectPropertyDefinitions<DO>.addReference(
    getter: (DO) -> AnyPropertyReference?
) =
    this.contextual(
        index = 1u,
        getter = getter,
        definition = ContextualPropertyReferenceDefinition<RequestContext>(
            required = false,
            contextualResolver = {
                it?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                    ?: throw ContextNotFoundException()
            }
        ),
        capturer = { context, value ->
            @Suppress("UNCHECKED_CAST")
            context.reference = value as IsPropertyReference<*, IsSerializablePropertyDefinition<*, *>, *>
        }
    )

internal val mapOfValidationExceptionDefinitions = mapOf(
    ALREADY_EXISTS to EmbeddedObjectDefinition(dataModel = { AlreadyExistsException }),
    ALREADY_SET to EmbeddedObjectDefinition(dataModel = { AlreadySetException }),
    INVALID_SIZE to EmbeddedObjectDefinition(dataModel = { InvalidSizeException }),
    INVALID_VALUE to EmbeddedObjectDefinition(dataModel = { InvalidValueException }),
    OUT_OF_RANGE to EmbeddedObjectDefinition(dataModel = { OutOfRangeException }),
    REQUIRED to EmbeddedObjectDefinition(dataModel = { RequiredException }),
    NOT_ENOUGH_ITEMS to EmbeddedObjectDefinition(dataModel = { NotEnoughItemsException }),
    TOO_MANY_ITEMS to EmbeddedObjectDefinition(dataModel = { TooManyItemsException }),
    UMBRELLA to EmbeddedObjectDefinition(dataModel = { ValidationUmbrellaException })
)
