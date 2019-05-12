package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationExceptionType
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.mapOfValidationExceptionDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.StatusType.VALIDATION_FAIL
import maryk.core.values.SimpleObjectValues

/** Failure in validation with [exceptions] */
data class ValidationFail<DM : IsRootDataModel<*>>(
    val exceptions: List<ValidationException>
) : IsAddResponseStatus<DM>, IsChangeResponseStatus<DM> {
    constructor(validationException: ValidationException) : this(
        if (validationException is ValidationUmbrellaException) {
            validationException.exceptions
        } else {
            listOf(validationException)
        }
    )

    override val statusType = VALIDATION_FAIL

    internal companion object : SimpleQueryDataModel<ValidationFail<*>>(
        properties = object : ObjectPropertyDefinitions<ValidationFail<*>>() {
            init {
                add(
                    1u, "exceptions",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = InternalMultiTypeDefinition(
                            typeEnum = ValidationExceptionType,
                            definitionMap = mapOfValidationExceptionDefinitions
                        )
                    ),
                    getter = ValidationFail<*>::exceptions,
                    toSerializable = { TypedValue(it.validationExceptionType, it) },
                    fromSerializable = { it.value }
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValidationFail<*>>) =
            ValidationFail<IsRootDataModel<IsPropertyDefinitions>>(
                exceptions = values(1u)
            )
    }
}
