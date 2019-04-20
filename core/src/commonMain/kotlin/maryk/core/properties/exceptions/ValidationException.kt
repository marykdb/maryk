package maryk.core.properties.exceptions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.IsChangeableValueDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
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

    internal companion object {
        internal fun <DO : ValidationException> addReference(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> AnyPropertyReference?
        ) {
            definitions.add(
                index = 1u, name = "reference",
                definition = ContextualPropertyReferenceDefinition<RequestContext>(
                    required = false,
                    contextualResolver = {
                        it?.dataModel?.properties as? AbstractPropertyDefinitions<*>?
                            ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.reference = value as IsPropertyReference<*, IsChangeableValueDefinition<*, *>, *>
                }
            )
        }

        internal fun <DO : ValidationException> addValue(
            definitions: ObjectPropertyDefinitions<DO>,
            getter: (DO) -> String?
        ) {
            definitions.add(
                2u, "value",
                StringDefinition(),
                getter
            )
        }
    }
}

internal val mapOfValidationExceptionDefinitions = mapOf(
    ValidationExceptionType.ALREADY_SET to EmbeddedObjectDefinition(dataModel = { AlreadySetException }),
    ValidationExceptionType.INVALID_SIZE to EmbeddedObjectDefinition(dataModel = { InvalidSizeException }),
    ValidationExceptionType.INVALID_VALUE to EmbeddedObjectDefinition(dataModel = { InvalidValueException }),
    ValidationExceptionType.OUT_OF_RANGE to EmbeddedObjectDefinition(dataModel = { OutOfRangeException }),
    ValidationExceptionType.REQUIRED to EmbeddedObjectDefinition(dataModel = { RequiredException }),
    ValidationExceptionType.NOT_ENOUGH_ITEMS to EmbeddedObjectDefinition(dataModel = { NotEnoughItemsException }),
    ValidationExceptionType.TOO_MANY_ITEMS to EmbeddedObjectDefinition(dataModel = { TooManyItemsException }),
    ValidationExceptionType.UMBRELLA to EmbeddedObjectDefinition(dataModel = { ValidationUmbrellaException })
)
