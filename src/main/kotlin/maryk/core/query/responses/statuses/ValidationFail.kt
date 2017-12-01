package maryk.core.query.responses.statuses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.exceptions.ValidationException
import maryk.core.properties.exceptions.ValidationUmbrellaException
import maryk.core.properties.exceptions.mapOfValidationExceptionDefinitions
import maryk.core.properties.types.TypedValue

/** Failure in validation
 * @param exceptions which were encountered
 */
data class ValidationFail<DO: Any>(
        val exceptions: List<ValidationException>
) : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO> {
    constructor(umbrellaException: ValidationUmbrellaException) : this(umbrellaException.exceptions)

    override val statusType = StatusType.VALIDATION_FAIL

    internal object Properties : PropertyDefinitions<ValidationFail<*>>() {
        val exceptions = ListDefinition(
                name = "exceptions",
                index = 0,
                required = true,
                valueDefinition = MultiTypeDefinition(
                        required = true,
                        getDefinition = { mapOfValidationExceptionDefinitions.get(it) }
                )
        )
    }

    companion object: QueryDataModel<ValidationFail<*>>(
            definitions = listOf(
                    Def(Properties.exceptions, { it.exceptions.map { TypedValue(it.validationExceptionType.index, it) } })
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ValidationFail<Any>(
                exceptions = (map[0] as List<TypedValue<ValidationException>>?)?.map { it.value } ?: emptyList()
        )
    }
}