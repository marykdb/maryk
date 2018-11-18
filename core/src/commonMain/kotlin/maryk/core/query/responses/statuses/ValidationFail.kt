package maryk.core.query.responses.statuses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationExceptionType
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.mapOfValidationExceptionDefinitions
import maryk.core.properties.types.TypedValue

/** Failure in validation with [exceptions] */
data class ValidationFail<DM: IsRootDataModel<*>>(
    val exceptions: List<ValidationException>
) : IsAddResponseStatus<DM>, IsChangeResponseStatus<DM> {
    constructor(umbrellaException: ValidationUmbrellaException) : this(umbrellaException.exceptions)

    override val statusType = StatusType.VALIDATION_FAIL

    internal companion object: SimpleQueryDataModel<ValidationFail<*>>(
        properties = object : ObjectPropertyDefinitions<ValidationFail<*>>() {
            init {
                add(1, "exceptions",
                    ListDefinition(
                        default = emptyList(),
                        valueDefinition = MultiTypeDefinition(
                            typeEnum = ValidationExceptionType,
                            definitionMap = mapOfValidationExceptionDefinitions
                        )
                    ),
                    getter = ValidationFail<*>::exceptions,
                    toSerializable = { TypedValue(it.validationExceptionType, it) },
                    fromSerializable = { it.value as ValidationException }
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ValidationFail<*>>) = ValidationFail<IsRootDataModel<IsPropertyDefinitions>>(
            exceptions = values(1)
        )
    }
}
