package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.mapOfValidationExceptionDefinitions
import maryk.core.properties.types.TypedValue

/** Failure in validation with [exceptions] */
data class ValidationFail<DO: Any>(
    val exceptions: List<ValidationException>
) : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO> {
    constructor(umbrellaException: ValidationUmbrellaException) : this(umbrellaException.exceptions)

    override val statusType = StatusType.VALIDATION_FAIL

    internal companion object: QueryDataModel<ValidationFail<*>>(
        properties = object : PropertyDefinitions<ValidationFail<*>>() {
            init {
                add(0, "exceptions",
                    ListDefinition(
                        valueDefinition = MultiTypeDefinition(
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
        override fun invoke(map: Map<Int, *>) = ValidationFail<Any>(
            exceptions = map(0, emptyList())
        )
    }
}
