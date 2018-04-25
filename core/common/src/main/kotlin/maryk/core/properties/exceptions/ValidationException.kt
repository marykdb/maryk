package maryk.core.properties.exceptions

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
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
        internal fun <DO: ValidationException> addReference(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsPropertyReference<*, *>?) {
            definitions.add(
                index = 0, name = "reference",
                definition = ContextCaptureDefinition(
                    ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                        required = false,
                        contextualResolver = {
                            it?.dataModel?.properties ?: throw ContextNotFoundException()
                        }
                    )
                ) { context, value ->
                    context?.apply {
                        @Suppress("UNCHECKED_CAST")
                        reference = value as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *>>
                    } ?: throw ContextNotFoundException()
                },
                getter = getter
            )
        }
        internal fun <DO: ValidationException> addValue(definitions: PropertyDefinitions<DO>, getter: (DO) -> String?) {
            definitions.add(
                1, "value",
                StringDefinition(),
                getter
            )
        }
    }
}

internal val mapOfValidationExceptionDefinitions = mapOf(
    ValidationExceptionType.ALREADY_SET to SubModelDefinition(dataModel = { AlreadySetException }),
    ValidationExceptionType.INVALID_SIZE to SubModelDefinition(dataModel = { InvalidSizeException }),
    ValidationExceptionType.INVALID_VALUE to SubModelDefinition(dataModel = { InvalidValueException }),
    ValidationExceptionType.OUT_OF_RANGE to SubModelDefinition(dataModel = { OutOfRangeException }),
    ValidationExceptionType.REQUIRED to SubModelDefinition(dataModel = { RequiredException }),
    ValidationExceptionType.NOT_ENOUGH_ITEMS to SubModelDefinition(dataModel = { NotEnoughItemsException }),
    ValidationExceptionType.TOO_MUCH_ITEMS to SubModelDefinition(dataModel = { TooMuchItemsException }),
    ValidationExceptionType.UMBRELLA to SubModelDefinition(dataModel = { ValidationUmbrellaException })
)
