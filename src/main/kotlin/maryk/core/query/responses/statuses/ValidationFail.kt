package maryk.core.query.responses.statuses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.exceptions.PropertyValidationUmbrellaException

/** Failure in validation
 * @param exceptions which were encountered
 */
data class ValidationFail<DO: Any>(
        val exceptions: PropertyValidationUmbrellaException
) : IsAddResponseStatus<DO>, IsChangeResponseStatus<DO> {
    override val statusType = StatusType.VALIDATION_FAIL

    companion object: QueryDataModel<ValidationFail<*>>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                ValidationFail<Any>(
                        exceptions = it[0] as PropertyValidationUmbrellaException
                )
            },
            definitions = listOf()
    )
}