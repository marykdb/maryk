package maryk.core.properties.exceptions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.AbstractPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.IsPropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/** Validation Exception with [newMessage] for properties */
abstract class ValidationException internal constructor(
    newMessage: String
) : Throwable(
    newMessage
) {
    internal constructor(
        reason: String?,
        reference: IsPropertyReference<*,*>?
    ) : this(
        newMessage = "Property «${reference?.completeName}» $reason"
    )

    internal abstract val validationExceptionType: ValidationExceptionType

    internal companion object {
        internal fun <DO: ValidationException> addReference(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> IsPropertyReference<*, *>?) {
            definitions.add(
                index = 0, name = "reference",
                definition = ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                    required = false,
                    contextualResolver = {
                        it?.dataModel?.properties as? AbstractPropertyDefinitions<*>? ?: throw ContextNotFoundException()
                    }
                ),
                getter = getter,
                capturer = { context, value ->
                    @Suppress("UNCHECKED_CAST")
                    context.reference = value as IsPropertyReference<*, IsPropertyDefinitionWrapper<*, *, *, *>>
                }
            )
        }
        internal fun <DO: ValidationException> addValue(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> String?) {
            definitions.add(
                1, "value",
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
    ValidationExceptionType.TOO_MUCH_ITEMS to EmbeddedObjectDefinition(dataModel = { TooMuchItemsException }),
    ValidationExceptionType.UMBRELLA to EmbeddedObjectDefinition(dataModel = { ValidationUmbrellaException })
)
