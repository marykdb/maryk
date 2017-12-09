package maryk.core.properties.exceptions

import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.properties.definitions.contextual.ContextCaptureDefinition
import maryk.core.properties.definitions.contextual.ContextualPropertyReferenceDefinition
import maryk.core.properties.definitions.wrapper.PropertyDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.DataModelPropertyContext

/**
 * Validation Exception for properties
 */
abstract class ValidationException(
        newMessage: String
) : Throwable(
        newMessage
) {
    constructor(
            reason: String?,
            reference: IsPropertyReference<*,*>?
    ) : this(
            newMessage = "Property «${reference?.completeName}» $reason"
    )

    abstract val validationExceptionType: ValidationExceptionType

    companion object {
        fun <DO: ValidationException> addReference(definitions: PropertyDefinitions<DO>, getter: (DO) -> IsPropertyReference<*, *>?) {
            definitions.add(
                    index = 0, name = "reference",
                    definition = ContextCaptureDefinition(
                            ContextualPropertyReferenceDefinition<DataModelPropertyContext>(
                                    required = false,
                                    contextualResolver = { it!!.dataModel!! }
                            )
                    ) { context, value ->
                        @Suppress("UNCHECKED_CAST")
                        context!!.reference = value as IsPropertyReference<*, PropertyDefinitionWrapper<*, *, *, *>>
                    },
                    getter = getter
            )
        }
        fun <DO: ValidationException> addValue(definitions: PropertyDefinitions<DO>, getter: (DO) -> String?) {
            definitions.add(
                    1, "value",
                    StringDefinition(),
                    getter
            )
        }
    }
}

internal val mapOfValidationExceptionDefinitions = mapOf(
        ValidationExceptionType.ALREADY_SET.index to SubModelDefinition(dataModel = { AlreadySetException }),
        ValidationExceptionType.INVALID_SIZE.index to SubModelDefinition(dataModel = { InvalidSizeException }),
        ValidationExceptionType.INVALID_VALUE.index to SubModelDefinition(dataModel = { InvalidValueException }),
        ValidationExceptionType.OUT_OF_RANGE.index to SubModelDefinition(dataModel = { OutOfRangeException }),
        ValidationExceptionType.REQUIRED.index to SubModelDefinition(dataModel = { RequiredException }),
        ValidationExceptionType.TOO_LITTLE_ITEMS.index to SubModelDefinition(dataModel = { TooLittleItemsException }),
        ValidationExceptionType.TOO_MUCH_ITEMS.index to SubModelDefinition(dataModel = { TooMuchItemsException }),
        ValidationExceptionType.UMBRELLA.index to SubModelDefinition(dataModel = { ValidationUmbrellaException })
)
