package maryk.core.query.responses

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.properties.definitions.EnumDefinition
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
    object Properties {
        val message = StringDefinition(
                name = "message",
                index = 0,
                required = true
        )
        val failType = EnumDefinition(
                name = "failType",
                index = 1,
                required = true,
                values = FailType.values()
        )
    }

    companion object: QueryDataModel<FailedActionResponse>(
            construct = {
                @Suppress("UNCHECKED_CAST")
                FailedActionResponse(
                        message = it[0] as String,
                        failType = it[1] as FailType
                )
            },
            definitions = listOf(
                    Def(Properties.message, FailedActionResponse::message),
                    Def(Properties.failType, FailedActionResponse::failType)
            )
    )
}
