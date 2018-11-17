package maryk.core.query.responses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Types of failures */
enum class FailType(
    override val index: Int
) : IndexedEnum<FailType> {
    CONNECTION(1), // Problems with Connection at the server
    STORE_STATE(2), // Problems with the state of the store
    REQUEST(3), // Problems with the request content
    AUTH(4); // Problems with the Authentication

    companion object: IndexedEnumDefinition<FailType>("FailType", FailType::values)
}

/** Response with [message] and [failType] for failed actions. */
data class FailedActionResponse(
    val message: String,
    val failType: FailType
) : IsResponse {
    internal companion object: SimpleQueryDataModel<FailedActionResponse>(
        properties = object : ObjectPropertyDefinitions<FailedActionResponse>() {
            init {
                add(1, "message", StringDefinition(), FailedActionResponse::message)
                add(2, "failType", EnumDefinition(enum = FailType), FailedActionResponse::failType)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<FailedActionResponse>) = FailedActionResponse(
            message = map(1),
            failType = map(2)
        )
    }
}
