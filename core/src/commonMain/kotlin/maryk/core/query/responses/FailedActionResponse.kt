package maryk.core.query.responses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.values.SimpleObjectValues

/** Types of failures */
enum class FailType(
    override val index: UInt
) : IndexedEnum<FailType> {
    CONNECTION(1u), // Problems with Connection at the server
    STORE_STATE(2u), // Problems with the state of the store
    REQUEST(3u), // Problems with the request content
    AUTH(4u); // Problems with the Authentication

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
        override fun invoke(values: SimpleObjectValues<FailedActionResponse>) = FailedActionResponse(
            message = values(1),
            failType = values(2)
        )
    }
}
