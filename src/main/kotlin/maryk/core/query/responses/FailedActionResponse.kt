package maryk.core.query.responses

import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.types.IndexedEnum

/** Types of failures */
enum class FailType(
        override val index: Int
) : IndexedEnum<FailType> {
    CONNECTION(0), // Problems with Connection at the server
    STORE_STATE(1), // Problems with the state of the store
    REQUEST(2), // Problems with the request content
    AUTH(3) // Problems with the Authentication
}

/** Response for failed actions.
 * @param message describing what went wrong
 * @param failType Type of failure that occurred
 */
data class FailedActionResponse(
        val message: String,
        val failType: FailType
) : IsResponse {
    companion object: QueryDataModel<FailedActionResponse>(
            properties = object : PropertyDefinitions<FailedActionResponse>() {
                init {
                    add(0, "message", StringDefinition(
                            required = true
                    ), FailedActionResponse::message)

                    add(1, "failType", EnumDefinition(
                            required = true,
                            values = FailType.values()
                    ), FailedActionResponse::failType)
                }
            }
    ) {
        override fun invoke(map: Map<Int, *>) = FailedActionResponse(
                message = map[0] as String,
                failType = map[1] as FailType
        )
    }
}
