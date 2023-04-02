package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.list
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationExceptionType
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.mapOfValidationExceptionDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.StatusType.VALIDATION_FAIL
import maryk.core.values.SimpleObjectValues

/** Failure in validation with [exceptions] */
data class ValidationFail<DM : IsRootDataModel>(
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

    /** Create an UmbrellaException of the Fail, so it can be thrown */
    @Suppress("unused")
    fun createUmbrellaException() = ValidationUmbrellaException(
        null,
        this.exceptions
    )

    @Suppress("unused")
    internal companion object : SimpleQueryModel<ValidationFail<*>>() {
        val exceptions by list(
            index = 1u,
            getter = ValidationFail<*>::exceptions,
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = ValidationExceptionType,
                definitionMap = mapOfValidationExceptionDefinitions
            ),
            toSerializable = { TypedValue(it.validationExceptionType, it) },
            fromSerializable = { it.value }
        )

        override fun invoke(values: SimpleObjectValues<ValidationFail<*>>) =
            ValidationFail<IsRootDataModel>(
                exceptions = values(1u)
            )
    }
}
